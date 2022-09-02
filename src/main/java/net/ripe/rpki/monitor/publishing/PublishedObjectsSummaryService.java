package net.ripe.rpki.monitor.publishing;

import com.google.common.base.Verify;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.monitor.metrics.PublishedObjectMetrics;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.SECONDS;

@AllArgsConstructor
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

    @Autowired
    private final PublishedObjectMetrics publishedObjectMetrics;
    public record RepositoryKey(String tag, String url){}
    public record RepositoryObjectTypeKey(@Delegate RepositoryKey key, RepositoryObjectType type) {}
    public record RepositoryDiffKey (RepositoryKey lhs, RepositoryKey rhs, RepositoryObjectType type, Duration threshold) {}
    public record RepositoryDiff (RepositoryDiffKey key, Set<RepositoryEntry> entries) {}

    /**
     * Update the repository size counters per object type.
     */
    public void updateSizes(Instant now, RepositoryTracker repository) {
        var key = repository.key();
        publishedObjectMetrics.trackObjectCount(key, repository.view(now));

        repository
            .view(now)
            .stream()
            .collect(Collectors.groupingBy(o -> o.getObjectType()))
            .forEach((objectType, value) -> {
                publishedObjectMetrics.trackObjectTypeCount(key, objectType, value.size());
            });
    }

    /**
     * Diff all repositories on the left-hand side with those on the right-hand
     * side and update the difference counters.
     */
    public Map<RepositoryDiffKey, Set<RepositoryEntry>> getDiff(Instant t, List<RepositoryTracker> lhss, List<RepositoryTracker> rhss) {
        var threshold = THRESHOLDS.stream().min(Duration::compareTo)
                .orElseThrow(() -> new IllegalStateException("PublishedObjectsSummaryService.THRESHOLDS is empty"));

        var diffs = new HashMap<RepositoryDiffKey, Set<RepositoryEntry>>();
        Sets.cartesianProduct(Set.copyOf(lhss), Set.copyOf(rhss)).stream().forEach((repoPair) -> {
            Verify.verify(repoPair.size() == 2, "invariant violated: not a tuple"); // invariant
            var lhs = repoPair.get(0); var rhs = repoPair.get(1);
            var lhsKey = lhs.key(); var rhsKey = rhs.key();

            for (var objectType: RepositoryObjectType.values()) {
                // lhs -> rhs, rhs -> lhs
                diffs.put(new RepositoryDiffKey(lhsKey, rhsKey, objectType, threshold), collectPublishedObjectDifferenceAndUpdateCounters(lhs, rhs, t, threshold, objectType).entries);
                diffs.put(new RepositoryDiffKey(rhsKey, lhsKey, objectType, threshold), collectPublishedObjectDifferenceAndUpdateCounters(rhs, lhs, t, threshold, objectType).entries);
            }
        });
        return diffs;
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public Map<RepositoryDiffKey, Set<RepositoryEntry>> updateAndGetPublishedObjectsDiff(Instant now, List<RepositoryTracker> repositories) {
        // n-choose-2 - for each unordered pair, calculate the difference in two directions
        return Sets.combinations(Set.copyOf(repositories), 2).stream().flatMap(repoSet -> {
            Verify.verify(repoSet.size() == 2, "invariant violated: not a tuple");
            var it = repoSet.iterator();

            return updateAndGetPublishedObjectsDiff(now, it.next(), it.next());
        }).collect(Collectors.toMap(RepositoryDiff::key, RepositoryDiff::entries));
    }

    /**
     * Process an update of repository (<code>lhs</code>).
     *
     * This updates its corresponding tracker and the difference counters
     * against the other repository (<code>rhs</code>).
     *
     * <emph>Calculates the difference in both directions</emph>
     */
    public Stream<RepositoryDiff> updateAndGetPublishedObjectsDiff(Instant now, RepositoryTracker lhs, RepositoryTracker rhs) {
        var res = Stream.<RepositoryDiff>builder();

        for (var threshold : THRESHOLDS) {
            for (var objectType: RepositoryObjectType.values()) {
                // lhs -> rhs
                res.add(collectPublishedObjectDifferenceAndUpdateCounters(lhs, rhs, now, threshold, objectType));
                // rhs -> lhs
                res.add(collectPublishedObjectDifferenceAndUpdateCounters(rhs, lhs, now, threshold, objectType));
            }
        }

        return res.build();
    }

    /**
     * Expose the max threshold so we can setup the {@link RepositoryTracker}
     * to keep discarded objects around long enough.
     */
    public Duration maxThreshold() {
        return THRESHOLDS.stream().max(Duration::compareTo)
                .orElseThrow(() -> new IllegalStateException("PublishedObjectsSummaryService.THRESHOLDS is empty"));
    }

    /**
     * Calculate the **one way** published object count difference.
     */
    private RepositoryDiff collectPublishedObjectDifferenceAndUpdateCounters(
        RepositoryTracker lhsTracker, RepositoryTracker rhsTracker, Instant now, Duration threshold, RepositoryObjectType objectType) {

        var diffKey = new RepositoryDiffKey(lhsTracker.key(), rhsTracker.key(), objectType, threshold);

        var diff = lhsTracker.difference(rhsTracker, now, threshold, objectType);
        publishedObjectMetrics.trackDiffSize(diffKey, diff.size());

        return new RepositoryDiff(diffKey, diff);
    }
}
