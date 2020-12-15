package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableSortedSet;
import lombok.NonNull;
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
import net.ripe.rpki.monitor.util.Sha256;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectAndDateCollector {

    private final RepoFetcher repoFetcher;
    private final CollectorUpdateMetrics collectorUpdateMetrics;
    private final RepositoryObjects repositoryObjects;

    public ObjectAndDateCollector(
        @NonNull final RepoFetcher repoFetcher,
        @NonNull CollectorUpdateMetrics metrics,
        RepositoryObjects repositoryObjects) {
        this.repoFetcher = repoFetcher;
        this.collectorUpdateMetrics = metrics;
        this.repositoryObjects = repositoryObjects;
    }

    public void run() throws FetcherException {
        final var passedObjects = new AtomicInteger();
        final var failedObjects = new AtomicInteger();

        try {
            final var expirationSummary = repoFetcher.fetchObjects().entrySet().parallelStream().map(e -> {
                var objectUri = e.getKey();
                var object = e.getValue();

                var statusAndObject = getDateFor(objectUri, object.getBytes());
                if (statusAndObject.getLeft()) {
                    passedObjects.incrementAndGet();
                } else {
                    failedObjects.incrementAndGet();
                }

                return statusAndObject.getRight().map(expiration -> new RepoObject(expiration, objectUri, Sha256.asBytes(object.getBytes())));
            }).filter(Optional::isPresent).map(Optional::get).collect(ImmutableSortedSet.toImmutableSortedSet(RepoObject::compareTo));

            repositoryObjects.setRepositoryObject(repoFetcher.repositoryUrl(), expirationSummary);

            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), passedObjects.get(), failedObjects.get());
        } catch (SnapshotNotModifiedException e) {
            collectorUpdateMetrics.trackSuccessWithoutUpdatedCounts(getClass().getSimpleName());
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), passedObjects.get(), failedObjects.get());
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
    Pair<Boolean, Optional<Date>> getDateFor(final String objectUri, final byte[] decoded) {

        final RepositoryObjectType objectType = RepositoryObjectType.parse(objectUri);

        try {
            switch (objectType) {
                case Manifest:
                    ManifestCmsParser manifestCmsParser = new ManifestCmsParser();
                    manifestCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    return Pair.of(true, Optional.of(manifestCmsParser.getManifestCms().getNextUpdateTime().toDate()));
                case Roa:
                    RoaCmsParser roaCmsParser = new RoaCmsParser();
                    roaCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    return Pair.of(true, Optional.of(roaCmsParser.getRoaCms().getNotValidAfter().toDate()));
                case Certificate:
                    X509CertificateParser x509CertificateParser = new X509ResourceCertificateParser();
                    x509CertificateParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final Date notAfter = x509CertificateParser.getCertificate().getCertificate().getNotAfter();
                    return Pair.of(true, Optional.of(notAfter));
                case Crl:
                    final X509Crl x509Crl = X509Crl.parseDerEncoded(decoded, ValidationResult.withLocation(objectUri));
                    final Date nextUpdate = x509Crl.getCrl().getNextUpdate();
                    return Pair.of(true, Optional.of(nextUpdate));
                case Gbr:
                    GhostbustersCmsParser ghostbustersCmsParser = new GhostbustersCmsParser();
                    ghostbustersCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    return Pair.of(true, Optional.of(ghostbustersCmsParser.getGhostbustersCms().getNotValidAfter().toDate()));
                default:
                    return Pair.of(true, Optional.empty());
            }
        } catch (Exception e) {
            return Pair.of(false, Optional.empty());
        }
    }


}
