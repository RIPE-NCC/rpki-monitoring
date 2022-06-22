package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private final Map<String, AtomicLong> counters1 = new ConcurrentHashMap<>();

    @Autowired
    public PublishedObjectsSummaryService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Update the repository size counters per object type.
     */
    public void updateSizes(Instant now, RepositoryTracker repository) {

        counters.computeIfAbsent(repository.getTag(), tag -> {
            var sizeCount = new AtomicLong(0);
            Metrics.buildObjectCountGauge(registry, sizeCount, tag);
            return sizeCount;
        }).set(repository.view(now).size());

        repository
            .view(now)
            .stream()
            .collect(Collectors.groupingBy(o -> o.getObjectType()))
            .forEach((objectType, value) -> {
                var type = asString(objectType);
                final String perTypeTag = perTypeTag(repository.getTag(), type);
                final AtomicLong counter = counters.computeIfAbsent(perTypeTag, tag -> {
                    var sizeCount = new AtomicLong(0);
                    Metrics.buildObjectCountGauge(registry, sizeCount, repository.getTag(), type);
                    return sizeCount;
                });
                counter.set(value.size());
            });
    }

    /**
     * Diff all repositories on the left-hand side with those on the right-hand
     * side and update the difference counters.
     */
    public Map<String, Set<RepositoryEntry>> getDiff(Instant t, List<RepositoryTracker> lhss, List<RepositoryTracker> rhss) {
        var threshold = THRESHOLDS.stream().min(Duration::compareTo)
                .orElseThrow(() -> new IllegalStateException("PublishedObjectsSummaryService.THRESHOLDS is empty"));
        var diffs = new HashMap<String, Set<RepositoryEntry>>();
        for (var lhs : lhss) {
            for (var rhs : rhss) {
                for (var objectType: RepositoryObjectType.values()) {
                    diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(lhs, rhs, t, threshold, objectType));
                }
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
                for (var objectType: RepositoryObjectType.values()) {
                    diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(lhs, rhs, now, threshold, objectType));
                }
            }
        }
        return diffs;
    }

    /**
     * Expose the max threshold so we can setup the {@link RepositoryTracker}
     * to keep discarded objects around long enough.
     */
    public Duration maxThreshold() {
        return THRESHOLDS.stream().max(Duration::compareTo)
                .orElseThrow(() -> new IllegalStateException("PublishedObjectsSummaryService.THRESHOLDS is empty"));
    }

    private Map<String, Set<RepositoryEntry>> collectPublishedObjectDifferencesAndUpdateCounters(
        RepositoryTracker lhs, RepositoryTracker rhs, Instant now, Duration threshold, RepositoryObjectType objectType) {

        var type = asString(objectType);

        var diffCounter = getOrCreateDiffCounter(lhs, rhs, threshold, objectType);
        var diffCounterInv = getOrCreateDiffCounter(rhs, lhs, threshold, objectType);

        var diff = lhs.difference(rhs, now, threshold, objectType);
        var diffInv = rhs.difference(lhs, now, threshold, objectType);

        diffCounter.set(diff.size());
        diffCounterInv.set(diffInv.size());

        return Map.of(
            perTypeDiffTag(lhs.getUrl(), rhs.getUrl(), threshold, type), diff,
            perTypeDiffTag(rhs.getUrl(), lhs.getUrl(), threshold, type), diffInv
        );
    }

    private AtomicLong getOrCreateDiffCounter(RepositoryTracker lhs, RepositoryTracker rhs, Duration threshold, RepositoryObjectType objectType) {
        var s = asString(objectType);
        final String tag = perTypeDiffTag(lhs.getTag(), rhs.getTag(), threshold, s);
        return counters.computeIfAbsent(tag, newTag -> {
            final var diffCount = new AtomicLong(0);
            Metrics.buildObjectDiffGauge(registry, diffCount, lhs.getTag(), lhs.getUrl(), rhs.getTag(), rhs.getUrl(), threshold, s);
            return diffCount;
        });
    }

    private static String perTypeTag(String tag, String objectType) {
        return String.format("%s-%s", tag, objectType);
    }

    private static String perTypeDiffTag(String lhs, String rhs, Duration threshold, String objectType) {
        return String.format("%s-diff-%s-%d-%s", lhs, rhs, threshold.getSeconds(), objectType);
    }

    private static String asString(RepositoryObjectType objectType) {
        return objectType.name().toLowerCase(Locale.ROOT);
    }
}
