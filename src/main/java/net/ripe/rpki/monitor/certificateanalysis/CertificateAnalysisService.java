package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@ConditionalOnProperty(value = "certificate-analysis.enabled", havingValue = "true", matchIfMissing = true)
public class CertificateAnalysisService {
    final CertificateAnalysisConfig config;

    final Tracer tracer;

    final Timer certificateComparisonDuration;

    final AtomicLong overlappingResourceCount = new AtomicLong();

    final AtomicLong overlappingCertificateCount = new AtomicLong();

    final AtomicLong totalCertificateCount = new AtomicLong();

    public CertificateAnalysisService(
            CertificateAnalysisConfig config,
            Optional<Tracer> maybeTracer,
            MeterRegistry meterRegistry) {
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

        Gauge.builder("rpkimonitoring.certificate.analysis.certificate.count",totalCertificateCount::get)
                .description("Total number of certificates analysed")
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
            log.info("{} overlaps", overlaps.size());
        } else {
            log.error("Too many overlaps ({}): Printing 3 sample overlaps:", overlaps.size());
            printOverlaps(overlaps.stream().limit(3).toList());
        }

        var overlappingResources = overlaps.stream().flatMap(Collection::stream).collect(IpResourceSet::new, (acc, rhs) -> acc.addAll(rhs.resources()), (comb, rhs) -> comb.addAll(rhs));
        var overlappingCertCount = overlaps.stream().flatMap(Collection::stream).distinct().count();
        var overlappingResourceCount = overlappingResources.stream().count();

        // Update metrics
        overlappingCertificateCount.set(overlappingCertCount);
        this.overlappingResourceCount.set(overlappingResourceCount);

        if (overlappingResourceCount > 21) {
            log.info("Not printing {} resources overlapping between certificates.", overlappingResourceCount);
        } else if (overlappingCertCount > 0) {
            log.info("Overlap between certs: {}", overlappingResources);
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
                    .filter(entry -> !config.isIgnoredFileName(entry.uri()))
                    .toList();
            totalCertificateCount.set(resourceCertificates.size());
            log.info("Expanded {} RPKI certificates", resourceCertificates.size());

            return certificateComparisonDuration.record(() -> compareCertificates(resourceCertificates));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to explore objects.", e);
            throw new RuntimeException(e);
        } finally {
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

        Set<Set<CertificateEntry>> overlaps = resourceCertificates.stream().flatMap(cert -> {
            var entriesStream = cert.resources().stream()
                    .flatMap(IpResourceUtil.flatMapComponentResources(resource -> lookupAllOverlappingEntries(nestedIntervalMap, resource)))
                    .flatMap(Collection::stream);

            var nonParents = entriesStream
                    .filter(entry -> !CertificateEntry.areAncestors(cert, entry))
                    .collect(Collectors.toSet());

            if (nonParents.size() > 1) {
                return Sets.combinations(nonParents, 2).stream();
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
