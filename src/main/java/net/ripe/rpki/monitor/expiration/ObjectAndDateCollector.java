package net.ripe.rpki.monitor.expiration;

import com.google.common.hash.BloomFilter;
import com.google.common.io.BaseEncoding;
import io.micrometer.tracing.Tracer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.GenericRpkiSignedObjectParser;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCmsParser;
import net.ripe.rpki.commons.crypto.cms.ghostbuster.GhostbustersCmsParser;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsParser;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationCheck;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.monitor.certificateanalysis.ObjectConsumer;
import net.ripe.rpki.monitor.expiration.fetchers.*;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.util.Sha256;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.ripe.rpki.commons.validation.ValidationString.ASPA_VERSION;
import static net.ripe.rpki.monitor.expiration.ObjectAndDateCollector.ObjectStatus.*;


@Slf4j
public class ObjectAndDateCollector {

    private final RepoFetcher repoFetcher;
    private final CollectorUpdateMetrics collectorUpdateMetrics;

    private final ObjectConsumer objectConsumer;

    private final Tracer tracer;

    /**
     * Bloom filter with 0.5% false positives (and no false negatives) at 100K objects to reduce logging.
     *
     * Using 3% at 10K would log 0,03*40000=1200 lines per time the repo is checked if a repo containing 40K files is
     * completely rejected (and the behaviour does not degrade due to being over capacity).
     */
    private final BloomFilter<String> loggedObjects = BloomFilter.create((from, into) -> into.putString(from, Charset.defaultCharset()), 100_000, 0.5);
    private final RepositoriesState repositoriesState;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private boolean acceptAspaV1;

    public ObjectAndDateCollector(
            @NonNull final RepoFetcher repoFetcher,
            @NonNull CollectorUpdateMetrics metrics,
            @NonNull RepositoriesState repositoriesState,
            @NonNull ObjectConsumer objectConsumer,
            @NonNull Tracer tracer,
            boolean acceptAspaV1) {
        this.repoFetcher = repoFetcher;
        this.collectorUpdateMetrics = metrics;
        this.repositoriesState = repositoriesState;
        this.objectConsumer = objectConsumer;
        this.tracer = tracer;
        this.acceptAspaV1 = acceptAspaV1;
    }

    @SuppressWarnings("try")
    public void run() throws FetcherException, RRDPStructureException, RepoUpdateFailedException {
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping updates of repository '{}' ({}) because a previous update is still running.", repoFetcher.meta().tag(), repoFetcher.meta().url());
            return;
        }

        var span = this.tracer.nextSpan()
                .name("ObjectAndDateCollector")
                .tag("tag", repoFetcher.meta().tag())
                .tag("url", repoFetcher.meta().url());
        final var passedObjects = new AtomicInteger();
        final var unknownObjects = new AtomicInteger();
        final var rejectedObjects = new AtomicInteger();
        final var maxObjectSize = new AtomicInteger();

        try (Tracer.SpanInScope ignored = this.tracer.withSpan(span.start())) {
            var rpkiObjects = repoFetcher.fetchObjects();

            var expirationSummary = calculateExpirationSummary(passedObjects, unknownObjects, rejectedObjects, maxObjectSize, rpkiObjects);
            span.event("expiration summary: done");

            objectConsumer.accept(rpkiObjects);

            repositoriesState.updateByTag(repoFetcher.meta().tag(), Instant.now(), expirationSummary.map(RepositoryEntry::from));

            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), repoFetcher.meta().tag(), repoFetcher.meta().url()).objectCount(passedObjects.get(), rejectedObjects.get(), unknownObjects.get(), maxObjectSize.get());
        } catch (SnapshotNotModifiedException e) {
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), repoFetcher.meta().tag(), repoFetcher.meta().url());
        } catch (RepoUpdateAbortedException e) {
            collectorUpdateMetrics.trackAborted(getClass().getSimpleName(), repoFetcher.meta().tag(), repoFetcher.meta().url());
        } catch (Exception e) {
            // Includes RepoUpdateFailedException
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), repoFetcher.meta().tag(), repoFetcher.meta().url()).objectCount(passedObjects.get(), rejectedObjects.get(), unknownObjects.get(), maxObjectSize.get());
            throw e;
        } finally {
            running.set(false);
            span.end();
        }
    }

    private Stream<RepoObject> calculateExpirationSummary(AtomicInteger passedObjects, AtomicInteger unknownObjects, AtomicInteger rejectedObjects, AtomicInteger maxObjectSize, Map<String, RpkiObject> rpkiObjects) {
        return rpkiObjects.entrySet().parallelStream().map(e -> {
            var objectUri = e.getKey();
            var object = e.getValue();

            var statusAndObject = getDateFor(objectUri, object.bytes());
            maxObjectSize.getAndAccumulate(object.bytes().length, Integer::max);
            if (ACCEPTED.equals(statusAndObject.getLeft())) {
                passedObjects.incrementAndGet();
            }
            if (UNKNOWN.equals(statusAndObject.getLeft())) {
                unknownObjects.incrementAndGet();
            }
            if (REJECTED.equals(statusAndObject.getLeft())){
                rejectedObjects.incrementAndGet();
            }

            return statusAndObject.getRight().map(validityPeriod -> new RepoObject(validityPeriod.creation(), validityPeriod.expiration(), objectUri, Sha256.asBytes(object.bytes())));
        }).flatMap(Optional::stream);
    }

    /**
     * Find the end of the validity period for the provided object.
     *
     * @param objectUri uri of object to decode
     * @param decoded content of the object
     * @return (was parseable, option[expiration data of object, if applicable])
     */
    Pair<ObjectStatus, Optional<ObjectValidityPeriod>> getDateFor(final String objectUri, final byte[] decoded) {
        final RepositoryObjectType objectType = RepositoryObjectType.parse(objectUri);

        try {
            return switch (objectType) {
                case Manifest -> {
                    ManifestCmsParser manifestCmsParser = new ManifestCmsParser();
                    manifestCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var manifestCms = manifestCmsParser.getManifestCms();
                    final var certificate = manifestCms.getCertificate().getCertificate();
                    final var notValidBefore = ObjectUtils.max(certificate.getNotBefore(), manifestCms.getThisUpdateTime().toDate());
                    final var notValidAfter = ObjectUtils.min(certificate.getNotAfter(), manifestCms.getNextUpdateTime().toDate());
                    yield acceptedObjectValidBetween(notValidBefore, notValidAfter);
                }
                case Aspa -> {
                    var aspaCmsParser = new AspaCmsParser();
                    var validationResult = ValidationResult.withLocation(objectUri);
                    aspaCmsParser.parse(validationResult, decoded);
                    // Handle the case of a v1 ASPA if configured to.
                    // This may be present in repositories that we monitor but do not control. In this case we do not
                    // want to reject objects, but can no longer parse these either
                    Predicate<ValidationCheck> isAspaV0Failure = check -> ASPA_VERSION.equals(check.getKey()) && Arrays.equals(new String[]{"0 [missing]"}, check.getParams());
                    if (acceptAspaV1 && validationResult.getFailuresForAllLocations().stream().allMatch(isAspaV0Failure)) {
                        yield Pair.of(ACCEPTED, genericParseValidityPeriod(decoded));
                    }

                    var aspaCms = aspaCmsParser.getAspa();
                    yield acceptedObjectValidBetween(
                            aspaCms.getNotValidBefore().toDate(),
                            aspaCms.getNotValidAfter().toDate()
                    );
                }
                case Roa -> {
                    RoaCmsParser roaCmsParser = new RoaCmsParser();
                    roaCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var roaCms = roaCmsParser.getRoaCms();
                    yield acceptedObjectValidBetween(
                            roaCms.getNotValidBefore().toDate(),
                            roaCms.getNotValidAfter().toDate()
                    );
                }
                case Certificate -> {
                    X509ResourceCertificateParser x509CertificateParser = new X509ResourceCertificateParser();
                    x509CertificateParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var cert = x509CertificateParser.getCertificate().getCertificate();
                    yield acceptedObjectValidBetween(
                            cert.getNotBefore(),
                            cert.getNotAfter()
                    );
                }
                case Crl -> {
                    final X509Crl x509Crl = X509Crl.parseDerEncoded(decoded, ValidationResult.withLocation(objectUri));
                    final var crl = x509Crl.getCrl();
                    yield acceptedObjectValidBetween(
                            crl.getThisUpdate(),
                            crl.getNextUpdate()
                    );
                }
                case Gbr -> {
                    GhostbustersCmsParser ghostbustersCmsParser = new GhostbustersCmsParser();
                    ghostbustersCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var ghostbusterCms = ghostbustersCmsParser.getGhostbustersCms();
                    yield acceptedObjectValidBetween(
                            ghostbusterCms.getNotValidBefore().toDate(),
                            ghostbusterCms.getNotValidAfter().toDate()
                    );
                }
                case Unknown -> {
                    var hash = Sha256.asString(decoded);
                    maybeLogObject(String.format("%s-%s-%s-%s-unknown", repoFetcher.meta().tag(), repoFetcher.meta().url(), objectUri, hash),
                                   "[{}-{}] Object at {} sha256(body)={} is unknown.", repoFetcher.meta().tag(), repoFetcher.meta().url(), objectUri, hash);
                    yield Pair.of(UNKNOWN, Optional.empty());
                }
            };
        } catch (Exception e) {
            var hash = Sha256.asString(decoded);
            maybeLogObject(String.format("%s-%s-%s-%s-rejected", repoFetcher.meta().tag(), repoFetcher.meta().url(), objectUri, hash),
                           "[{}-{}] Object at {} rejected: msg={} sha256(body)={}. body={}", repoFetcher.meta().tag(), repoFetcher.meta().url(), objectUri, e.getMessage(), hash, BaseEncoding.base64().encode(decoded));
            switch (objectType) {
                // Assume there was a problem that caused the object to be syntactically invalid.
                // Try to extract the validity period from the EE certificate for objects derived from a generic signed object.
                //
                // This would help during for example object profile changes (...)
                case Aspa:
                case Gbr:
                case Manifest:
                case Roa:
                    return Pair.of(REJECTED, genericParseValidityPeriod(decoded));
            }
            return Pair.of(REJECTED, Optional.empty());
        }
    }

    private Optional<ObjectValidityPeriod> genericParseValidityPeriod(byte[] decoded) {
        try {
            var parser = new GenericRpkiSignedObjectParser();
            parser.parse(ValidationResult.withLocation("unknown"), decoded);
            var validity = parser.getCertificate().getValidityPeriod();

            return Optional.of(new ObjectValidityPeriod(validity.getNotValidBefore().toDate().toInstant(), validity.getNotValidAfter().toDate().toInstant()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Log a message about an object if it (likely) has nog been logged before.
     *
     * <b>likely:</b> because a bloomfilter does not give guarantees about non-presence
     * @param key key to set in the bloom filter
     * @param message message - passed to log.info
     * @param arguments arguments passed to log.info
     */
    private void maybeLogObject(final String key, final String message, Object... arguments) {
        if (!loggedObjects.mightContain(key)) {
            log.info(message, arguments);
            loggedObjects.put(key);
        }
    }

    public enum ObjectStatus {
        ACCEPTED, UNKNOWN, REJECTED
    }

    public record ObjectValidityPeriod(Instant creation, Instant expiration) {
        public static ObjectValidityPeriod of(TemporalAccessor creation, TemporalAccessor expiration) {
            return new ObjectValidityPeriod(Instant.from(creation), Instant.from(expiration));
        }
    }

    private Pair<ObjectStatus, Optional<ObjectValidityPeriod>> acceptedObjectValidBetween(Date creation, Date expiration) {
        return Pair.of(ACCEPTED, Optional.of(new ObjectValidityPeriod(creation.toInstant(), expiration.toInstant())));
    }
}
