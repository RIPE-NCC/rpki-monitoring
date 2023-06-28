package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateParser;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CertificateAnalysisService {
    final CertificateAnalysisConfig config;

    final Tracer tracer;

    final Timer certificateComparisonDuration;
    final Timer certificateBatchDuration;

    public CertificateAnalysisService(
            CertificateAnalysisConfig config,
            Optional<Tracer> tracer,
            MeterRegistry meterRegistry) {
        this.tracer = tracer.orElse(Tracer.NOOP);
        this.config = config;
        certificateComparisonDuration = Timer.builder("rpki.monitor.certificate.comparison.duration")
                .description("Duration of certificate comparison (N^2)")
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(meterRegistry);
        certificateBatchDuration = Timer.builder("rpki.monitor.certificate.batch.duration")
                .description("Duration of certificate comparison (per 1000 comparisons)")
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(meterRegistry);
    }

    public void process(ImmutableMap<String, RpkiObject> rpkiObjectMap) {
        var allObjects = rpkiObjectMap.size();

        var certificates = rpkiObjectMap.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".cer"))
                .filter(entry -> config.isIgnoredFileName(entry.getKey()))
                .toList();
        log.info("Processing {} certificates out of {} objects", certificates.size(), allObjects);

        // Parse all certificates
        var routerCertificates = new AtomicInteger();
        // Get the _unique_ certificates
        var resourceCertificates = certificates.parallelStream().map(object -> {
            var encoded = object.getValue().getBytes();
            ValidationResult validationResult = ValidationResult.withLocation(object.getKey());
            var cert = X509CertificateParser.parseCertificate(validationResult, encoded);

            boolean router = cert.isRouter();
            if (!router) {
                var parser = new X509ResourceCertificateParser();
                parser.parse(validationResult, encoded);
                return Optional.of(new CertificateEntry(URI.create(object.getKey()), parser.getCertificate()));
            } else {
                routerCertificates.incrementAndGet();
                return Optional.<CertificateEntry>empty();
            }

        }).filter(Optional::isPresent).map(Optional::get)
                .distinct() // ignore duplicate certificates
                .collect(Collectors.toUnmodifiableSet());

        log.info("Found {} router certificates", routerCertificates.get());

        // Naïeve O(n^2) implementation (n choose 2) = n!/(k!(n-k)!)
        // = n!/(2!*(n-2)!) = n*(n-1)*(n-2)!/(2*1*(n-2)!)
        // = n*(n-1)/2
        log.info("Starting certificate comparison of {} certs", resourceCertificates.size());
        var overlap = certificateComparisonDuration.record(() -> compareCertificates(resourceCertificates));
        log.info("Finished certificate comparison of {} certs", resourceCertificates.size());

        var overlapCount = overlap.stream().count();
        if (overlapCount > 64) {
            log.info("Not printing {} overlaps between certificates.", overlapCount);
        } else {
            log.info("Overlap between certs: {}", overlap);
        }
    }

    private ImmutableResourceSet compareCertificates(Set<CertificateEntry> resourceCertificates) {
        var overlap = new AtomicReference<>(ImmutableResourceSet.of());

        var overlapCount = new AtomicInteger();
        var processedCerts = new AtomicInteger();

        var batchStart = new AtomicLong(System.currentTimeMillis());

        if (resourceCertificates.size() >= 2) {
            Sets.combinations(resourceCertificates, 2).parallelStream().forEach(pair -> {
                // prevent some object construction and copying by using iterator to access.
                var it = pair.iterator();

                var cert1 = it.next();
                var cert2 = it.next();

                var intersection = cert1.resources().intersection(cert2.resources());
                if (!intersection.isEmpty()) {
                    var count = overlapCount.incrementAndGet();
                    if (count <= 10) {
                        log.info("Found intersection between {} ({}) and {} ({}) of {}", cert1.subject(), cert1.uri(), cert2.subject(), cert2.uri(), intersection);
                        if (count == 10) {
                            log.info("Not printing further overlaps.");
                        }
                    }
                    overlap.accumulateAndGet(intersection, (a, b) -> new ImmutableResourceSet.Builder().addAll(a).addAll(b).build());
                }

                var processedCount = processedCerts.getAndIncrement();
                if (processedCount % 1000 == 0) {
                    if (processedCount % 1000 == 0) {
                        batchStart.updateAndGet(now -> {
                            var t1 = System.currentTimeMillis();
                            certificateBatchDuration.record(Duration.ofMillis(t1 - now));

                            return t1;
                        });
                    }
                }
            });
        }

        return overlap.get();
    }

    record CertificateEntry(URI uri, X500Principal subject, ImmutableResourceSet resources) {
        public CertificateEntry(URI uri, X509ResourceCertificate certificate) {
            this(uri, certificate.getSubject(), ImmutableResourceSet.of(certificate.getResources()));
        }
    }
}
