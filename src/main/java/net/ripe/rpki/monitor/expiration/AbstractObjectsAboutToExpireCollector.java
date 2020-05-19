package net.ripe.rpki.monitor.expiration;

import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractObjectsAboutToExpireCollector {
    public final static String COLLECTOR_UPDATE_DESCRIPTION = "Number of updates by collector by status";
    public static final String COLLECTOR_UPDATE_METRIC = "collector.update";

    private final RepoFetcher repoFetcher;

    private final AtomicLong lastUpdated = new AtomicLong();

    private final Counter successCount;
    private final Counter failureCount;


    public AbstractObjectsAboutToExpireCollector(@NonNull final RepoFetcher repoFetcher, @NonNull final MeterRegistry registry) {
        this.repoFetcher = repoFetcher;

        Gauge.builder("collector.lastupdated", lastUpdated::get)
                .description("Last update by collector")
                .tag("fetcher", repoFetcher.getClass().getSimpleName())
                .register(registry);

        successCount = Counter.builder(COLLECTOR_UPDATE_METRIC)
                .description(COLLECTOR_UPDATE_DESCRIPTION)
                .tag("fetcher", repoFetcher.getClass().getSimpleName())
                .tag("status", "success")
                .register(registry);

        failureCount = Counter.builder("collector.update")
                .description(COLLECTOR_UPDATE_DESCRIPTION)
                .tag("fetcher", repoFetcher.getClass().getSimpleName())
                .tag("status", "failure")
                .register(registry);
    }

    protected abstract void setSummary(final ConcurrentSkipListSet<RepoObject> expirationSummary);


    public void run() throws FetcherException {

        final ConcurrentSkipListSet<RepoObject> expirationSummary = new ConcurrentSkipListSet();

        try {
            final Map<String, byte[]> stringMap = repoFetcher.fetchObjects();

            stringMap.forEach((objectUri, object) -> {
                final Optional<Date> date = getDateFor(objectUri, object);
                date.stream().forEach(d -> expirationSummary.add(new RepoObject(d, objectUri, Hashing.sha256().hashBytes(object).asBytes())));
            });

            setSummary(expirationSummary);
            successCount.increment();
            lastUpdated.set(System.currentTimeMillis() / 1000);
        } catch (SnapshotNotModifiedException e) {
            successCount.increment();
            lastUpdated.set(System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            failureCount.increment();

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
