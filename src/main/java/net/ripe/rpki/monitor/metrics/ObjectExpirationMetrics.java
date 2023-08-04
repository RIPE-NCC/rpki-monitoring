package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
public class ObjectExpirationMetrics {
    private static final String COLLECTOR_CREATION_DESCRIPTION = "Number of counted objects by collector by time since creation";
    public static final String COLLECTOR_CREATION_METRIC = "rpkimonitoring.collector.creation";

    private static final String COLLECTOR_EXPIRATION_DESCRIPTION = "Number of counted objects by collector by time to expiration";
    public static final String COLLECTOR_EXPIRATION_METRIC = "rpkimonitoring.collector.expiration";

    private static final double[] SERVICE_LEVEL_INDICATORS = new double[]{
        Duration.ofMinutes(1).toSeconds(),
        Duration.ofMinutes(30).toSeconds(),
        Duration.ofHours(1).toSeconds(),
        Duration.ofHours(4).toSeconds(),
        Duration.ofHours(7).toSeconds(),
        Duration.ofHours(8).toSeconds(),  // All manifests and CRLs are likely created within the last 8 hours.
        Duration.ofHours(13).toSeconds(), // manifests MUST be refreshed before they have 13h left
        Duration.ofHours(15).toSeconds(), // manifests and SHOULD be refreshed by this timestamp
        Duration.ofHours(24).toSeconds(), // validity of CRLs, manifest eContent, manifest EE cert (6486-bis 5.1.2)
        Duration.ofDays(7).toSeconds(),
        Duration.ofDays(182).toSeconds(), // ROA EE certs, CAs have 18 months validity, track 6,12 months
        Duration.ofDays(365).toSeconds(),
    };

    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, RepositoryExpirationSummary> expirationSummaries = new ConcurrentHashMap<>();

    public void trackExpiration(String url, Instant t, Stream<RepositoryEntry> content) {
        final var updateHistogram = getExpirationSummary(url);

        content.parallel().forEach(obj -> updateHistogram.update(t, obj));
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
                    .distributionStatisticExpiry(Duration.ofMinutes(15))
                    .minimumExpectedValue((double)Duration.ofMinutes(5).toSeconds())
                    .maximumExpectedValue((double)Duration.ofDays(366).toSeconds())
                    .serviceLevelObjectives(SERVICE_LEVEL_INDICATORS)
                    .register(registry);

            expirationHistogram = DistributionSummary.builder(COLLECTOR_EXPIRATION_METRIC)
                    .description(COLLECTOR_EXPIRATION_DESCRIPTION)
                    .tag("url", repoUrl)
                    .baseUnit("seconds")
                    .distributionStatisticExpiry(Duration.ofMinutes(15))
                    .minimumExpectedValue((double)Duration.ofMinutes(5).toSeconds())
                    .maximumExpectedValue((double)Duration.ofDays(366).toSeconds())
                    .serviceLevelObjectives(SERVICE_LEVEL_INDICATORS)
                    .register(registry);
        }

        public void update(Instant now, RepositoryEntry object) {
            object.expiration().ifPresent(expiration ->
                    expirationHistogram.record(now.until(expiration, ChronoUnit.SECONDS)));
            object.creation().ifPresent(creation ->
                    creationHistogram.record(creation.until(now, ChronoUnit.SECONDS)));
        }
    }
}
