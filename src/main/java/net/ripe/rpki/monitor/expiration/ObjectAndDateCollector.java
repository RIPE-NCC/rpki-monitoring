package net.ripe.rpki.monitor.expiration;

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

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

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

        final ConcurrentSkipListSet<RepoObject> expirationSummary = new ConcurrentSkipListSet<>();

        try {
            repoFetcher.fetchObjects().forEach((objectUri, object) -> {
                final Optional<Date> date = getDateFor(objectUri, object.getBytes());
                date.ifPresent(d -> expirationSummary.add(new RepoObject(d, objectUri, Sha256.asBytes(object))));
            });

            repositoryObjects.setRepositoryObject(repoFetcher.repositoryUrl(), expirationSummary);

            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName());
        } catch (SnapshotNotModifiedException e) {
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName());
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName());
            throw e;
        }
    }

    Optional<Date> getDateFor(final String objectUri, final byte[] decoded) {

        final RepositoryObjectType objectType = RepositoryObjectType.parse(objectUri);

        switch (objectType) {
            case Manifest:
                ManifestCmsParser manifestCmsParser = new ManifestCmsParser();
                manifestCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                return Optional.of(manifestCmsParser.getManifestCms().getNextUpdateTime().toDate());
            case Roa:
                RoaCmsParser roaCmsParser = new RoaCmsParser();
                roaCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                return Optional.of(roaCmsParser.getRoaCms().getNotValidAfter().toDate());
            case Certificate:
                X509CertificateParser x509CertificateParser = new X509ResourceCertificateParser();
                x509CertificateParser.parse(ValidationResult.withLocation(objectUri), decoded);
                final Date notAfter = x509CertificateParser.getCertificate().getCertificate().getNotAfter();
                return Optional.of(notAfter);
            case Crl:
                final X509Crl x509Crl = X509Crl.parseDerEncoded(decoded, ValidationResult.withLocation(objectUri));
                final Date nextUpdate = x509Crl.getCrl().getNextUpdate();
                return Optional.of(nextUpdate);
            case Gbr:
                GhostbustersCmsParser ghostbustersCmsParser = new GhostbustersCmsParser();
                ghostbustersCmsParser.parse(ValidationResult.withLocation(objectUri), decoded);
                return Optional.of(ghostbustersCmsParser.getGhostbustersCms().getNotValidAfter().toDate());
            default:
                return Optional.empty();
        }
    }


}
