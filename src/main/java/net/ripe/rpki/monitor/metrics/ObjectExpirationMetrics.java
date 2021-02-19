package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Component
public class ObjectExpirationMetrics {
    private static final String COLLECTOR_CREATION_DESCRIPTION = "Number of objects by collector by time since creation";
    public static final String COLLECTOR_CREATION_METRIC = "rpkimonitoring.collector.creation";

    private static final String COLLECTOR_EXPIRATION_DESCRIPTION = "Number of objects by collector by time to expiration";
    public static final String COLLECTOR_EXPIRATION_METRIC = "rpkimonitoring.collector.expiration";

    @Autowired
    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, RepositoryExpirationSummary> expirationSummaries = new ConcurrentHashMap<>();

    public void trackExpiration(final String url, RepositoryObjects.RepositoryContent content) {
        final var now = Instant.now();
        final var updateHistogram = getExpirationSummary(url);

        content.getObjects().parallelStream().forEach(obj -> updateHistogram.update(now, obj));
    }

    private RepositoryExpirationSummary getExpirationSummary(final String repoUrl) {
        return expirationSummaries.computeIfAbsent(repoUrl, key -> new RepositoryExpirationSummary(repoUrl));
    }

    private class RepositoryExpirationSummary {
        private final DistributionSummary expirationHistogram;
        private final DistributionSummary creationHistogram;

        public RepositoryExpirationSummary(String repoUrl) {
            creationHistogram = DistributionSummary.builder(COLLECTOR_CREATION_METRIC)
                    .description(COLLECTOR_CREATION_DESCRIPTION)
                    .tag("url", repoUrl)
                    .baseUnit("seconds")
                    .publishPercentileHistogram()
                    .distributionStatisticExpiry(Duration.ofMinutes(15))
                    .minimumExpectedValue((double)Duration.ofMinutes(5).toSeconds())
                    .serviceLevelObjectives(
                            Duration.ofHours(1).toSeconds(),
                            Duration.ofHours(4).toSeconds(),
                            Duration.ofHours(7).toSeconds(),
                            Duration.ofHours(8).toSeconds(),
                            Duration.ofHours(24).toSeconds(), // CRLs, manifest eContent
                            Duration.ofDays(7).toSeconds(), // Manifest EE certs
                            Duration.ofDays(182).toSeconds(), // ROA EE certs, CAs have 18 months validity, track 6,12,18 months
                            Duration.ofDays(365).toSeconds(),
                            Duration.ofDays(548).toSeconds()
                    )
                    .register(registry);

            expirationHistogram = DistributionSummary.builder(COLLECTOR_EXPIRATION_METRIC)
                    .description(COLLECTOR_EXPIRATION_DESCRIPTION)
                    .tag("url", repoUrl)
                    .baseUnit("seconds")
                    .publishPercentileHistogram()
                    .distributionStatisticExpiry(Duration.ofMinutes(15))
                    .minimumExpectedValue((double)Duration.ofMinutes(5).toSeconds())
                    .serviceLevelObjectives(
                            Duration.ofHours(1).toSeconds(),
                            Duration.ofHours(4).toSeconds(),
                            Duration.ofHours(7).toSeconds(),
                            Duration.ofHours(8).toSeconds(),
                            Duration.ofHours(24).toSeconds(), // CRLs, manifest eContent
                            Duration.ofDays(7).toSeconds(), // Manifest EE certs
                            Duration.ofDays(182).toSeconds(), // ROA EE certs, CAs have 18 months validity, track 6,12,18 months
                            Duration.ofDays(365).toSeconds(),
                            Duration.ofDays(548).toSeconds()
                    )
                    .register(registry);
        }

        public void update(Instant now, RepoObject object) {
            expirationHistogram.record(now.until(object.getExpiration().toInstant(), ChronoUnit.SECONDS));
            creationHistogram.record(object.getCreation().toInstant().until(now, ChronoUnit.SECONDS));
        }
    }
}
