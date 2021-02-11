package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Component
public class ObjectExpirationMetrics {
    public static final String COLLECTOR_EXPIRATION_DESCRIPTION = "Number of objects by collector by time to expiration";
    public static final String COLLECTOR_EXPIRATION_METRIC = "rpkimonitoring.collector.expiration";

    @Autowired
    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, DistributionSummary> executionStatus = new ConcurrentHashMap<>();

    public void trackExpiration(final String url, RepositoryObjects.RepositoryContent content) {
        final var now = Instant.now();
        final var histogram = getDistributionSummary(url);

        content.getObjects().parallelStream().forEach(obj -> {
            histogram.record(now.until(obj.getExpirationDate().toInstant(), ChronoUnit.SECONDS));
        });
    }

    private DistributionSummary getDistributionSummary(final String repoUrl) {
        return executionStatus.computeIfAbsent(repoUrl, key ->
                DistributionSummary.builder(COLLECTOR_EXPIRATION_METRIC)
                    .description(COLLECTOR_EXPIRATION_DESCRIPTION)
                    .tag("url", repoUrl)
                    .baseUnit("seconds")
                    .publishPercentileHistogram()
                    .minimumExpectedValue((double)TimeUnit.MINUTES.toSeconds(5))
                    .maximumExpectedValue((double)TimeUnit.HOURS.toSeconds(25))
                    .serviceLevelObjectives(
                            TimeUnit.HOURS.toSeconds(1),
                            TimeUnit.HOURS.toSeconds(4),
                            TimeUnit.HOURS.toSeconds(8)
                    )
                    .register(registry)
        );
    }
}
