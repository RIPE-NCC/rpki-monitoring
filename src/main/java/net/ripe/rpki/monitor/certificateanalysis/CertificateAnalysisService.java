package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.BigIntegerMath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.IpResourceUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@ConditionalOnProperty(value = "certificate-analysis.enabled", havingValue = "true", matchIfMissing = true)
public class CertificateAnalysisService {
    public static final BigInteger MAX_PAIRS_PER_CERT = BigInteger.valueOf(128);
    public static final int MAX_TOTAL_PAIRS = 65_536;
    public static final int MAX_PRINTED_CERT_URLS = 50;
    public static final int MAX_PRINTED_RESOURCES = 10;
    final CertificateAnalysisConfig config;

    final Tracer tracer;

    final Timer certificateComparisonDuration;

    final AtomicLong overlappingResourceCount = new AtomicLong();

    final AtomicLong overlappingCertificateCount = new AtomicLong();

    final AtomicLong totalCertificateCount = new AtomicLong();

    final Counter processedPassed;
    final Counter processingFailed;

    public CertificateAnalysisService(
            CertificateAnalysisConfig config,
            Optional<Tracer> maybeTracer,
            MeterRegistry meterRegistry) {
        Preconditions.checkState(MAX_PAIRS_PER_CERT.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 0, "Maximum number of pairs processed needs to be below long overflow");

        this.tracer = maybeTracer.orElse(Tracer.NOOP);
        this.config = config;
        certificateComparisonDuration = Timer.builder("rpkimonitoring.certificate.analysis.comparison.duration")
                .description("Duration of certificate comparison (N^2)")
                .maximumExpectedValue(Duration.ofSeconds(300))
                .register(meterRegistry);

        Gauge.builder("rpkimonitoring.certificate.analysis.overlapping.certificate.count", overlappingCertificateCount::get)
                .description("Number of certificates with overlaps (number, not number of pairs)")
                .register(meterRegistry);
        Gauge.builder("rpkimonitoring.certificate.analysis.overlapping.resource.count", overlappingResourceCount::get)
                .description("Number of resources that overlap between certificates")
                .register(meterRegistry);

        Gauge.builder("rpkimonitoring.certificate.analysis.certificate.count", totalCertificateCount::get)
                .description("Total number of certificates analysed")
                .register(meterRegistry);

        processedPassed = Counter.builder("rpkimonitoring.certificate.analysis.runs")
                .description("Number of analysis runs by status")
                .tag("result", "success")
                .register(meterRegistry);
        processingFailed = Counter.builder("rpkimonitoring.certificate.analysis.runs")
                .description("Number of analysis runs by status")
                .tag("result", "failure")
                .register(meterRegistry);
    }

    protected Stream<CertificateEntry> extractCertificateSpan(ImmutableMap<String, RpkiObject> rpkiObjectMap) throws ExecutionException, InterruptedException {
        return ForkJoinPool.commonPool().submit(new ExtractRpkiCertificateSpan(rpkiObjectMap, config.getRootCertificateUrl(), "/")).get();
    }

    /**
     * Process the objects, filter the overlaps for our conditions on when to report, and update the metrics
     * @param rpkiObjectMap the objects
     */
    public List<Set<CertificateEntry>> process(ImmutableMap<String, RpkiObject> rpkiObjectMap) {
        var allOverlaps = processInternal(rpkiObjectMap);

        // Filter for inclusion criteria:
        // - SIA on allow-list
        // - Not in grace period that is caused by a keyroll
        var overlaps = allOverlaps.stream()
                .filter(config.hasOnlyTrackedSIA().and(config.hasAnyCertificateAfterGracePeriodStarts(Instant.now()).negate()))
                .toList();

        // After processing print a manageable, arbitrary number of overlaps
        if (overlaps.size() < 21) {
            log.info("{} overlaps after filtering for checked SIAs and key-roll grace period.", overlaps.size());
        } else {
            log.error("Too many overlaps after filtering for checked SIAs and key-roll grace period ({}): Printing 3 sample overlaps:", overlaps.size());
            printOverlaps(overlaps.stream().limit(3).toList());
        }

        var overlappingResources = overlaps.stream().flatMap(Collection::stream).collect(IpResourceSet::new, (acc, rhs) -> acc.addAll(rhs.resources()), (comb, rhs) -> comb.addAll(rhs));
        var overlappingCertCount = overlaps.stream().flatMap(Collection::stream).distinct().count();
        var overlappingResourceCount = overlappingResources.stream().count();

        // Update metrics
        overlappingCertificateCount.set(overlappingCertCount);
        this.overlappingResourceCount.set(overlappingResourceCount);

        if (overlappingResourceCount > 0) {
            log.info("Overlapping resources between certificates (max: " + MAX_PRINTED_RESOURCES +"): {}", overlappingResources.stream().limit(MAX_PRINTED_RESOURCES).map(IpResource::toString).collect(Collectors.joining(", ")));
        }

        if (overlappingCertCount > 0) {
            var overlappingCerts = overlaps.stream().flatMap(Collection::stream).distinct().limit(MAX_PRINTED_CERT_URLS);
            log.info("certificates with overlap (max: " + MAX_PRINTED_CERT_URLS + "): {}", overlappingCerts.map(CertificateEntry::uri).collect(Collectors.joining(", ")));
        }

        return overlaps;
    }

    /**
     * Calculate the overlap <emph>without</emph> filtering for when to include the detected overlap.
     */
    @SuppressWarnings("try")
    protected Set<Set<CertificateEntry>> processInternal(ImmutableMap<String, RpkiObject> rpkiObjectMap) {
        var span = this.tracer.nextSpan().name("certificate-analysis");

        log.info("Processing {} files", rpkiObjectMap.size());

        try (var ignored = this.tracer.withSpan(span.start())){
            // Top-down exploration via manifests
            var resourceCertificates = extractCertificateSpan(rpkiObjectMap)
                    .filter(entry -> !config.isFileInIgnoredOverlap(entry.uri()))
                    .toList();
            totalCertificateCount.set(resourceCertificates.size());
            log.info("Expanded {} RPKI certificates", resourceCertificates.size());

            var res = certificateComparisonDuration.record(() -> compareCertificates(resourceCertificates));
            processedPassed.increment();
            return res;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to explore objects.", e);
            processingFailed.increment();
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            log.error("Error while processing", e);
            processingFailed.increment();
            throw e;
        } finally {
            // On exception, value ends up being 1
            span.end();
        }
    }

    private static <T> void putAsSet(NestedIntervalMap<IpResource, Set<T>> target, IpResource resource, T value) {
        var cur = target.findExact(resource);
        if (cur != null) {
            cur.add(value);
        } else {
            var newEntry = new HashSet<T>();
            newEntry.add(value);
            target.put(resource, newEntry);
        }
    }

    static <V> Stream<V> lookupAllOverlappingEntries(NestedIntervalMap<IpResource, V> map, IpResource resource) {
        return Stream.concat(
                map.findExactAndAllLessSpecific(resource).stream(),
                map.findAllMoreSpecific(resource).stream()
        );
    }


    protected Set<Set<CertificateEntry>> compareCertificates(List<CertificateEntry> resourceCertificates) {
        // Build nested interval map for lookup.
        // note that entries can not overlap -> normalise entries for prefixes.
        var nestedIntervalMap = new NestedIntervalMap<IpResource, Set<CertificateEntry>>(IpResourceIntervalStrategy.getInstance());

        resourceCertificates.forEach(entry ->
            entry.resources().forEach(IpResourceUtil.forEachComponentResource((key) -> putAsSet(nestedIntervalMap, key, entry)))
        );

        final var totalOverlaps = new AtomicInteger();

        Set<Set<CertificateEntry>> overlaps = resourceCertificates.stream().unordered().parallel().flatMap(cert -> {
            if (totalOverlaps.get() > MAX_TOTAL_PAIRS) {
                throw new IllegalStateException("Too many overlaps to process, aborted at " + totalOverlaps.get());
            }

            var entriesStream = cert.resources().stream()
                    .flatMap(IpResourceUtil.flatMapComponentResources(resource -> lookupAllOverlappingEntries(nestedIntervalMap, resource)))
                    .flatMap(Collection::stream);

            var nonParents = entriesStream
                    .filter(entry -> !CertificateEntry.areAncestors(cert, entry))
                    .distinct()
                    .toList();


            if (nonParents.size() > 1) {
                // precondition for binomial: n >= k
                var numPairs = BigIntegerMath.binomial(nonParents.size(), 2);
                if (numPairs.compareTo(MAX_PAIRS_PER_CERT) > 0) {
                    log.error("Too many overlaps to process: ({} choose 2) = {}", nonParents.size(), numPairs.toString());
                    log.info("Involved URIs: {}", nonParents.stream().map(CertificateEntry::uri).collect(Collectors.joining(", ")));
                    return Stream.empty();
                }

                // pre-allocate the array
                var res = new ArrayList<Set<CertificateEntry>>(numPairs.intValueExact());

                for (int i=0; i < nonParents.size(); i++) {
                    for (int j=0; j < i; j++) {
                        res.add(Set.of(nonParents.get(i), nonParents.get(j)));
                    }
                }
                // postcondition: n-choose-2 pairs
                assert res.size() == numPairs.intValueExact();
                totalOverlaps.addAndGet(numPairs.intValueExact());

                return res.stream();
            }
            return Stream.empty();
        }).collect(Collectors.toSet());

        log.info("Finished overlap check of {} certs. |certificates with overlaps|: {}", resourceCertificates.size(), overlaps.size());
        return overlaps;
    }

    public static void printOverlaps(Collection<Set<CertificateEntry>> overlaps) {
        overlaps.forEach(pair -> {
                    var iter = pair.iterator();
                    var cert1 = iter.next();
                    var cert2 = iter.next();

                    var intersection = cert1.resources().intersection(cert2.resources());

                    var symmetricDifference = symmetricDifference(cert1.resources(), cert2.resources());

                    log.info("Found intersection between\n{} {} notBefore={} notAfter={} uri={} SIA={} and\n{} {} notBefore={} notAfter={} uri={} SIA={}.\nOverlap: {}.\nSymmetric difference: {}",
                            cert1.certificate().getSubject(), cert1.reachabilityPath(), cert1.certificate().getCertificate().getNotBefore(), cert1.certificate().getCertificate().getNotAfter(), cert1.uri(), cert1.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST),
                            cert2.certificate().getSubject(), cert2.reachabilityPath(), cert2.certificate().getCertificate().getNotBefore(), cert2.certificate().getCertificate().getNotAfter(), cert2.uri(), cert2.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST),
                            intersection,
                            symmetricDifference.isEmpty() ? "∅" : symmetricDifference
                    );
                });
    }

    // https://github.com/RIPE-NCC/ipresource/pull/16
    public static ImmutableResourceSet symmetricDifference(ImmutableResourceSet lhs, ImmutableResourceSet rhs) {
        return new ImmutableResourceSet.Builder()
                .addAll(lhs.difference(rhs))
                .addAll(rhs.difference(lhs))
                .build();
    }
}
