package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final RepositoryObjects repositoryObjects;
    private final CoreClient rpkiCoreClient;
    private final AppConfig appConfig;

    private final Map<String, AtomicLong> counters = new HashMap<>();
    private final MeterRegistry registry;

    private final Map<String, RepositoryTracker> repositories;

    @Autowired
    public PublishedObjectsSummaryService(
        final RepositoryObjects repositoryObjects,
        final CoreClient rpkiCoreClient,
        final MeterRegistry registry,
        final AppConfig appConfig
    ) {
        this.registry = registry;
        this.repositoryObjects = repositoryObjects;
        this.rpkiCoreClient = rpkiCoreClient;
        this.appConfig = appConfig;

        this.repositories = initRepositories(appConfig);
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public Map<String, Set<FileEntry>> updateAndGetPublishedObjectsDiff() {
        var now = Instant.now();

        // Update the repository trackers with the latest object information
        updateCoreRepository(now);
        updateRsyncRepositories(now);
        updateRrdpRepositories(now);

        return updateAndGetPublishedObjectsDiff(now,
                List.copyOf(repositories.values()));
    }

    private void updateCoreRepository(Instant now) {
        try {
            repositories.get("core").update(now, rpkiCoreClient.publishedObjects());
        } catch (Exception e) {
            log.error("Cannot fetch published objects from rpki-core", e);
        }
    }

    private void updateRsyncRepositories(Instant now) {
        repositories.get("rsync").update(now, repositoryObjects.getObjects(appConfig.getRsyncConfig().getMainUrl()));
        appConfig.getRsyncConfig().getOtherUrls().forEach(
                (tag, url) -> repositories.get("rsync-" + tag).update(now, repositoryObjects.getObjects(url))
        );
    }

    private void updateRrdpRepositories(Instant now) {
        repositories.get("rrdp").update(now, repositoryObjects.getObjects(appConfig.getRrdpConfig().getMainUrl()));
        appConfig.getRrdpConfig().getOtherUrls().forEach(
                (tag, url) -> repositories.get("rrdp-" + tag).update(now, repositoryObjects.getObjects(url))
        );
    }

    public Map<String, Set<FileEntry>> getRsyncDiff(Instant now, Duration threshold) {
        updateRsyncRepositories(now);
        var mainRepo = repositories.get("rsync");

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (var tag : appConfig.getRsyncConfig().getOtherUrls().keySet()) {
            var repo = repositories.get("rsync-" + tag);
            diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(mainRepo, repo, now, threshold));
        }
        return diffs;
    }

    public Map<String, Set<FileEntry>> getRrdpDiff(Instant now, Duration threshold) {
        updateRrdpRepositories(now);
        var mainRepo = repositories.get("rrdp");

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (var tag : appConfig.getRrdpConfig().getOtherUrls().keySet()) {
            var repo = repositories.get("rrdp-" + tag);
            diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(mainRepo, repo, now, threshold));
        }
        return diffs;
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public Map<String, Set<FileEntry>> updateAndGetPublishedObjectsDiff(Instant now, List<RepositoryTracker> repositories) {
        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
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
    public Map<String, Set<FileEntry>> updateAndGetPublishedObjectsDiff(Instant now, RepositoryTracker lhs, List<RepositoryTracker> rhss) {
        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        var counter = getOrCreateCounter(lhs.getTag());
        counter.set(lhs.size(now));

        for (var rhs : rhss) {

            for (var threshold : THRESHOLDS) {
                diffs.putAll(collectPublishedObjectDifferencesAndUpdateCounters(lhs, rhs, now, threshold));
            }
        }
        return diffs;
    }

    private Map<String, Set<FileEntry>> collectPublishedObjectDifferencesAndUpdateCounters(RepositoryTracker lhs, RepositoryTracker rhs, Instant now, Duration threshold) {
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

    private static Map<String, RepositoryTracker> initRepositories(AppConfig config) {
        var main = Stream.of(
                RepositoryTracker.empty("core", config.getCoreUrl()),
                RepositoryTracker.empty("rrdp", config.getRrdpConfig().getMainUrl()),
                RepositoryTracker.empty("rsync", config.getRsyncConfig().getMainUrl())
        );
        var extras = Stream.concat(
                config.getRsyncConfig().getOtherUrls()
                        .entrySet().stream()
                        .map(e -> RepositoryTracker.empty("rsync-" + e.getKey(), e.getValue())),
                config.getRrdpConfig().getOtherUrls()
                        .entrySet().stream()
                        .map(e -> RepositoryTracker.empty("rrdp-" + e.getKey(), e.getValue()))
        );

        return Stream.concat(main, extras)
                .collect(Collectors.toUnmodifiableMap(RepositoryTracker::getTag, Function.identity()));
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

    private AtomicLong getOrCreateCounter(String tag) {
        return counters.computeIfAbsent(tag, newTag -> {
            final var diffCount = new AtomicLong(0);
            Metrics.buildObjectCountGauge(registry, diffCount, tag);
            return diffCount;
        });
    }
}

