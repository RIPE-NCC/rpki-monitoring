package net.ripe.rpki.monitor.expiration;

import com.google.common.hash.BloomFilter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.ghostbuster.GhostbustersCmsParser;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsParser;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateParser;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotNotModifiedException;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.util.Sha256;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static net.ripe.rpki.monitor.expiration.ObjectAndDateCollector.ObjectStatus.*;


@Slf4j
public class ObjectAndDateCollector {

    private final RepoFetcher repoFetcher;
    private final CollectorUpdateMetrics collectorUpdateMetrics;

    /**
     * Bloom filter with 0.5% false positives (and no false negatives) at 100K objects to reduce logging.
     *
     * Using 3% at 10K would log 0,03*40000=1200 lines per time the repo is checked if a repo containing 40K files is
     * completely rejected (and the behaviour does not degrade due to being over capacity).
     */
    private final BloomFilter<String> loggedObjects = BloomFilter.create((from, into) -> into.putString(from, Charset.defaultCharset()), 100_000, 0.5);
    private final RepositoriesState repositoriesState;

    public ObjectAndDateCollector(
        @NonNull final RepoFetcher repoFetcher,
        @NonNull CollectorUpdateMetrics metrics,
        @NonNull RepositoriesState repositoriesState) {
        this.repoFetcher = repoFetcher;
        this.collectorUpdateMetrics = metrics;
        this.repositoriesState = repositoriesState;
    }

    public void run() throws FetcherException {
        final var passedObjects = new AtomicInteger();
        final var unknownObjects = new AtomicInteger();
        final var rejectedObjects = new AtomicInteger();

        try {
            final var expirationSummary = repoFetcher.fetchObjects().entrySet().parallelStream().map(e -> {
                var objectUri = e.getKey();
                var object = e.getValue();

                var statusAndObject = getDateFor(objectUri, object.getBytes());
                if (ACCEPTED.equals(statusAndObject.getLeft())) {
                    passedObjects.incrementAndGet();
                }
                if (UNKNOWN.equals(statusAndObject.getLeft())) {
                    unknownObjects.incrementAndGet();
                }
                if (REJECTED.equals(statusAndObject.getLeft())){
                    rejectedObjects.incrementAndGet();
                }

                return statusAndObject.getRight().map(validityPeriod -> new RepoObject(validityPeriod.getCreation(), validityPeriod.getExpiration(), objectUri, Sha256.asBytes(object.getBytes())));
            }).flatMap(Optional::stream);

            repositoriesState.updateByUrl(repoFetcher.repositoryUrl(), Instant.now(), expirationSummary.map(RepositoryEntry::from));

            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), repoFetcher.repositoryUrl()).objectCount(passedObjects.get(), rejectedObjects.get(), unknownObjects.get());
        } catch (SnapshotNotModifiedException e) {
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), repoFetcher.repositoryUrl());
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), repoFetcher.repositoryUrl()).objectCount(passedObjects.get(),  rejectedObjects.get(), unknownObjects.get());
            throw e;
        }
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
            switch (objectType) {
                case Manifest:
                    ManifestCmsParser manifestCmsParser = new ManifestCmsParser();
                    manifestCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var manifestCms = manifestCmsParser.getManifestCms();
                    final var certificate = manifestCms.getCertificate().getCertificate();
                    final var notValidBefore = ObjectUtils.max(certificate.getNotBefore(), manifestCms.getThisUpdateTime().toDate());
                    final var notValidAfter = ObjectUtils.min(certificate.getNotAfter(), manifestCms.getNextUpdateTime().toDate());
                    return acceptedObjectValidBetween(notValidBefore, notValidAfter);
                case Roa:
                    RoaCmsParser roaCmsParser = new RoaCmsParser();
                    roaCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var roaCms = roaCmsParser.getRoaCms();
                    return acceptedObjectValidBetween(
                            roaCms.getNotValidBefore().toDate(),
                            roaCms.getNotValidAfter().toDate()
                    );
                case Certificate:
                    X509CertificateParser x509CertificateParser = new X509ResourceCertificateParser();
                    x509CertificateParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var cert = x509CertificateParser.getCertificate().getCertificate();
                    return acceptedObjectValidBetween(
                            cert.getNotBefore(),
                            cert.getNotAfter()
                    );
                case Crl:
                    final X509Crl x509Crl = X509Crl.parseDerEncoded(decoded, ValidationResult.withLocation(objectUri));
                    final var crl = x509Crl.getCrl();
                    return acceptedObjectValidBetween(
                            crl.getThisUpdate(),
                            crl.getNextUpdate()
                    );
                case Gbr:
                    GhostbustersCmsParser ghostbustersCmsParser = new GhostbustersCmsParser();
                    ghostbustersCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var ghostbusterCms = ghostbustersCmsParser.getGhostbustersCms();
                    return acceptedObjectValidBetween(
                            ghostbusterCms.getNotValidBefore().toDate(),
                            ghostbusterCms.getNotValidAfter().toDate()
                    );
                default:
                    maybeLogObject(String.format("%s-%s-unknown", repoFetcher.repositoryUrl(), objectUri),
                                   "[{}] Object at {} is unknown.", repoFetcher.repositoryUrl(), objectUri);
                    return Pair.of(UNKNOWN, Optional.empty());
            }
        } catch (Exception e) {
            maybeLogObject(String.format("%s-%s-rejected", repoFetcher.repositoryUrl(), objectUri),
                           "[{}] Object at {} rejected: {}.", repoFetcher.repositoryUrl(), objectUri, e.getMessage());
            return Pair.of(REJECTED, Optional.empty());
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

    public String repositoryUrl() {
        return repoFetcher.repositoryUrl();
    }

    public enum ObjectStatus {
        ACCEPTED, UNKNOWN, REJECTED
    }

    @Value(staticConstructor = "of")
    public static class ObjectValidityPeriod {
        Date creation;
        Date expiration;
    }

    private Pair<ObjectStatus, Optional<ObjectValidityPeriod>> acceptedObjectValidBetween(Date creation, Date expiration) {
        return Pair.of(ACCEPTED, Optional.of(ObjectValidityPeriod.of(creation, expiration)));
    }
}
