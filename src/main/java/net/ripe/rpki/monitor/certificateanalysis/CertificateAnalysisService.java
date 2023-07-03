package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class CertificateAnalysisService {
    final CertificateAnalysisConfig config;

    final Tracer tracer;

    final Timer certificateComparisonDuration;

    public CertificateAnalysisService(
            CertificateAnalysisConfig config,
            Optional<Tracer> maybeTracer,
            MeterRegistry meterRegistry) {
        this.tracer = maybeTracer.orElse(Tracer.NOOP);
        this.config = config;
        certificateComparisonDuration = Timer.builder("rpki.monitor.certificate.comparison.duration")
                .description("Duration of certificate comparison (N^2)")
                .maximumExpectedValue(Duration.ofSeconds(300))
                .register(meterRegistry);
    }

    public void process(ImmutableMap<String, RpkiObject> rpkiObjectMap) {
        var allObjects = rpkiObjectMap.size();
        log.info("Processing {} files", rpkiObjectMap.size());


        try {
            // Top-down exploration via manifests
            var resourceCertificates = ForkJoinPool.commonPool().submit(new ExtractRpkiCertificateSpan(rpkiObjectMap, config.getRootCertificateUrl())).get()
                    .filter(entry -> !config.isIgnoredFileName(entry.uri()))
                    .collect(Collectors.toList());
            log.info("Expanded {} RPKI certificates", resourceCertificates.size());

            // Naïeve O(n^2) implementation (n choose 2) = n!/(k!(n-k)!)
            // = n!/(2!*(n-2)!) = n*(n-1)*(n-2)!/(2*1*(n-2)!)
            // = n*(n-1)/2
            var overlap = certificateComparisonDuration.record(() -> compareCertificates(resourceCertificates));

            var overlapCount = overlap.stream().count();
            if (overlapCount > 21) {
                log.info("Not printing {} resources overlapping between certificates.", overlapCount);
            } else {
                log.info("Overlap between certs: {}", overlap);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to explore objects.", e);
        }

    }


    private ImmutableResourceSet compareCertificates(List<CertificateEntry> resourceCertificates) {
        var totalCerts = resourceCertificates.size();
        log.info("Starting certificate comparison of {} certs", totalCerts);
        var overlap = new AtomicReference<>(ImmutableResourceSet.of());

        var overlapCount = new AtomicInteger();

        AtomicInteger comparisonsIter = new AtomicInteger();
        IntStream.range(0, totalCerts).parallel().forEach(i -> {
            // intersection only needs to be determined in one direction between distinct certs
            IntStream.range(0, totalCerts).forEach(j -> {
                if (i < j) {
                    comparisonsIter.getAndIncrement();
                    var cert1 = resourceCertificates.get(i);
                    var cert2 = resourceCertificates.get(j);

                    var intersection = cert1.resources().intersection(cert2.resources());
                    if (!intersection.isEmpty()) {
                        var count = overlapCount.incrementAndGet();
                        if (count < 50) {
                            var symmetricDifference = symmetricDifference(cert1.resources(), cert2.resources());

                            log.info("Found intersection between {} (uri={} SIA={}) and {} (uri={} SIA={}). Overlap: {}. Symmetric difference: {}",
                                    cert1.certificate().getSubject(), cert1.uri(), cert1.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST),
                                    cert2.certificate().getSubject(), cert2.uri(), cert2.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST),
                                    intersection,
                                    symmetricDifference.isEmpty() ? "∅" : symmetricDifference
                            );
                        } else if (count == 50) {
                            log.info("Not printing further overlaps.");
                        }
                        overlap.accumulateAndGet(intersection, (a, b) -> new ImmutableResourceSet.Builder().addAll(a).addAll(b).build());
                    }
                }
            });
        });

        log.info("Finished {} certificate comparison of {} certs. |certificates with overlaps|: {}", comparisonsIter.get(), resourceCertificates.size(), overlapCount.get());
        return overlap.get();
    }

    record CertificateOverlap(List<OverlappingResourceCertificates> overlappingCertificatePairs) {
        public ImmutableResourceSet overlap() {
            return this.overlappingCertificatePairs.stream()
                    .map(OverlappingResourceCertificates::overlap)
                    .collect(ImmutableResourceSet::of
                            , ImmutableResourceSet::union, ImmutableResourceSet::union);
        }
    }

    record OverlappingResourceCertificates(X509ResourceCertificate lhs, X509ResourceCertificate rhs) {
        public ImmutableResourceSet overlap() {
            return ImmutableResourceSet.of(lhs.getResources()).intersection(ImmutableResourceSet.of(rhs.getResources()));
        }
    }

    // https://github.com/RIPE-NCC/ipresource/pull/16
    public ImmutableResourceSet symmetricDifference(ImmutableResourceSet lhs, ImmutableResourceSet rhs) {
        return new ImmutableResourceSet.Builder()
                .addAll(lhs.difference(rhs))
                .addAll(rhs.difference(lhs))
                .build();
    }
}
