package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;

@Setter
@Service
@Slf4j
public class PublishedObjectsSummaryService {
    static final List<Duration> THRESHOLDS = List.of(
            Duration.of(256, SECONDS),
            Duration.of(596, SECONDS),
            Duration.of(851, SECONDS),
            Duration.of(1024, SECONDS),
            Duration.of(1706, SECONDS),
            Duration.of(3411, SECONDS)
    );

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Autowired
    public PublishedObjectsSummaryService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Update the repository size counter.
     */
    public void updateSize(Instant now, RepositoryTracker repository) {
        counters.computeIfAbsent(repository.getTag(), tag -> {
            var sizeCount = new AtomicLong(0);
            Metrics.buildObjectCountGauge(registry, sizeCount, tag);
            return sizeCount;
        }).set(repository.size(now));
    }

    /**
     * Diff all repositories on the left-hand side with those on the right-hand
     * side and update the difference counters.
     */
    public Map<String, Set<RepositoryEntry>> getDiff(Instant t, List<RepositoryTracker> lhss, List<RepositoryTracker> rhss) {
        var threshold = THRESHOLDS.stream().min(Duration::compareTo).get();
        var diffs = new HashMap<String, Set<RepositoryEntry>>();
        for (var lhs : lhss) {
            for (var rhs : rhss) {
                diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(lhs, rhs, t, threshold));
            }
        }
        return diffs;
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public Map<String, Set<RepositoryEntry>> updateAndGetPublishedObjectsDiff(Instant now, List<RepositoryTracker> repositories) {
        var diffs = new HashMap<String, Set<RepositoryEntry>>();
        // n-choose-2
        // It is safe to only generate subsets of size 2 in one order because
        // we calculate the difference in two directions.
        for (var lhs : repositories) {
            var rhss = repositories.stream().takeWhile(x -> x != lhs).collect(Collectors.toList());
            diffs.putAll(updateAndGetPublishedObjectsDiff(now, lhs, rhss));
        }
        return diffs;
    }

    /**
     * Process an update of repository (<code>lhs</code>).
     *
     * This updates its corresponding tracker and the difference counters
     * against the other repositories (<code>rhss</code>).
     */
    public Map<String, Set<RepositoryEntry>> updateAndGetPublishedObjectsDiff(Instant now, RepositoryTracker lhs, List<RepositoryTracker> rhss) {
        final Map<String, Set<RepositoryEntry>> diffs = new HashMap<>();
        for (var rhs : rhss) {
            for (var threshold : THRESHOLDS) {
                diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(lhs, rhs, now, threshold));
            }
        }
        return diffs;
    }

    private Map<String, Set<RepositoryEntry>> collectPublishedObjectDifferencesAndUpdateCounters(RepositoryTracker lhs, RepositoryTracker rhs, Instant now, Duration threshold) {
        var diffCounter = getOrCreateDiffCounter(lhs, rhs, threshold);
        var diffCounterInv = getOrCreateDiffCounter(rhs, lhs, threshold);

        var diff = lhs.difference(rhs, now, threshold);
        var diffInv = rhs.difference(lhs, now, threshold);

        diffCounter.set(diff.size());
        diffCounterInv.set(diffInv.size());

        return Map.of(
                diffTag(lhs.getUrl(), rhs.getUrl(), threshold), diff,
                diffTag(rhs.getUrl(), lhs.getUrl(), threshold), diffInv
        );
    }

    private AtomicLong getOrCreateDiffCounter(RepositoryTracker lhs, RepositoryTracker rhs, Duration threshold) {
        final String tag = diffTag(lhs.getTag(), rhs.getTag(), threshold);
        return counters.computeIfAbsent(tag, newTag -> {
            final var diffCount = new AtomicLong(0);
            Metrics.buildObjectDiffGauge(registry, diffCount, lhs.getTag(), lhs.getUrl(), rhs.getTag(), rhs.getUrl(), threshold);
            return diffCount;
        });
    }

    private static String diffTag(String lhs, String rhs, Duration threshold) {
        return String.format("%s-diff-%s-%d", lhs, rhs, threshold.getSeconds());
    }
}
