package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.MINUTES;

@Setter
@Service
public class PublishedObjectsSummaryService {

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
    public Map<String, Set<FileEntry>> getPublishedObjectsDiff() {
        var now = Instant.now();

        // Update the repository trackers with the latest object information
        repositories.get("core").update(now, rpkiCoreClient.publishedObjects());
        updateRsyncRepositories(now);
        updateRrdpRepositories(now);

        return getPublishedObjectsDiff(now,
                List.copyOf(repositories.values()));
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
            diffs.putAll(comparePublicationPoints(mainRepo, repo, now, threshold));
        }
        return diffs;
    }

    public Map<String, Set<FileEntry>> getRrdpDiff(Instant now, Duration threshold) {
        updateRrdpRepositories(now);
        var mainRepo = repositories.get("rrdp");

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (var tag : appConfig.getRrdpConfig().getOtherUrls().keySet()) {
            var repo = repositories.get("rrdp-" + tag);
            diffs.putAll(comparePublicationPoints(mainRepo, repo, now, threshold));
        }
        return diffs;
    }

    private Map<String, Set<FileEntry>> comparePublicationPoints(RepositoryTracker lhs, RepositoryTracker rhs, Instant now, Duration threshold) {
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

    public Map<String, Set<FileEntry>> getPublishedObjectsDiff(Instant now, List<RepositoryTracker> repositories) {
        final var thresholds = new Duration[]{Duration.of(5, MINUTES), Duration.of(15, MINUTES), Duration.of(30, MINUTES)};

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        // n-choose-2
        // It is safe to only generate subsets of size 2 in one order because
        // we calculate the difference in two directions.
        for (int i = 0; i < repositories.size(); i++) {
            var lhs = repositories.get(i);

            var counter = getOrCreateCounter(lhs.getTag());
            counter.set(lhs.size(now));

            for (int j = i+1; j < repositories.size(); j++) {
                var rhs = repositories.get(j);

                for (var threshold : thresholds) {
                    diffs.putAll(comparePublicationPoints(lhs, rhs, now, threshold));
                }
            }
        }
        return diffs;
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

/**
 * Track lifetime of objects in a single repository.
 *
 * More specifically, for each object the time its first seen in the repository
 * is tracked. This allows tracking differences in repositories with temporal
 * thresholds, since an object won't reach every repository at exactly the same
 * time.
 *
 * An instance of this class can be safely shared across threads.
 */
class RepositoryTracker {
    @Getter
    private final String tag;
    @Getter
    private final String url;

    private final AtomicReference<Map<String, Pair<FileEntry, Instant>>> objects;

    /**
     * Get an empty repository.
     */
    public static RepositoryTracker empty(String tag, String url) {
        return new RepositoryTracker(tag, url, Collections.emptyMap());
    }

    /**
     * Create a repository with the given entries and time <i>t</i>.
     */
    public static <T extends HasHashAndUri> RepositoryTracker with(String tag, String url, Instant t, Collection<T> entries) {
        var repo = RepositoryTracker.empty(tag, url);
        repo.update(t, entries);
        return repo;
    }

    private RepositoryTracker(String tag, String url, Map<String, Pair<FileEntry, Instant>> objects) {
        this.tag = tag;
        this.url = url;
        this.objects = new AtomicReference<>(objects);
    }

    /**
     * Update this repository with the given entries at time <i>t</i>.
     *
     * The repository's objects are <b>replaced</b> by the entries. Any objects
     * no longer present in the <code>entries</code> set are discarded from the
     * repository.
     */
    public <T extends HasHashAndUri> void update(Instant t, Collection<T> entries) {
        var newObjects = entries.stream()
                .map(x -> Pair.of(FileEntry.from(x), firstSeenAt(x.getSha256(), t)))
                .collect(Collectors.toUnmodifiableMap(x -> x.getLeft().getSha256(), Function.identity()));
        objects.set(newObjects);
    }

    /**
     * Get the (non-associative) difference between this and the other repository
     * at time <i>t</i>, not exceeding threshold.
     */
    public Set<FileEntry> difference(RepositoryTracker other, Instant t, Duration threshold) {
        return Sets.difference(entriesAt(t.minus(threshold)), other.entriesAt(t));
    }

    /**
     * Get the number of objects in this repository, at time <i>t</i>.
     */
    public int size(Instant t) {
        return entriesAt(t).size();
    }

    /**
     * Get all entries in the repository present at time <i>t</i>.
     *
     * Presence is defined as objects first seen before (or at) a given time.
     * I.e. any objects first seen after <i>t</i> are considered not present.
     */
    private Set<FileEntry> entriesAt(Instant t) {
        return objects.get().values().stream()
                .filter(x -> x.getRight().compareTo(t) <= 0)
                .map(Pair::getLeft).collect(Collectors.toSet());
    }

    private Instant firstSeenAt(String sha256, Instant now) {
        var previous = objects.get().get(sha256);
        return previous != null ? previous.getRight() : now;
    }
}
