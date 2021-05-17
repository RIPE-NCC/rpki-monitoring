package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MINUTES;

@Setter
@Service
public class PublishedObjectsSummaryService {

    private final RepositoryObjects repositoryObjects;
    private final CoreClient rpkiCoreClient;
    private final AppConfig appConfig;

    private final Map<String, AtomicLong> counters = new HashMap<>();
    private final MeterRegistry registry;

    private final RepositoryTracker core;
    private final RepositoryTracker rrdp;
    private final RepositoryTracker rsync;

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
        this.core = RepositoryTracker.empty("core", appConfig.getCoreUrl());
        this.rrdp = RepositoryTracker.empty("rrdp", appConfig.getRrdpConfig().getMainUrl());
        this.rsync = RepositoryTracker.empty("rsync", appConfig.getRsyncConfig().getMainUrl());
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public Map<String, Set<FileEntry>> getPublishedObjectsDiff() {
        var now = Instant.now();
        core.update(now, rpkiCoreClient.publishedObjects());
        rrdp.update(now, repositoryObjects.getObjects(rrdp.getUrl()));
        rsync.update(now, repositoryObjects.getObjects(rsync.getUrl()));

        return getPublishedObjectsDiff(now, core, rrdp, rsync);
    }

    public Map<String, Set<FileEntry>> getRsyncDiff(Instant now, Duration threshold) {
        // Update the main rsync repository once
        rsync.update(now, repositoryObjects.getObjects(rsync.getUrl()));

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (var secondary : appConfig.getRsyncConfig().getOtherUrls().entrySet()) {
            var tag = secondary.getKey();
            var url = secondary.getValue();
            var repo = RepositoryTracker.with(tag, url, now, repositoryObjects.getObjects(url));
            diffs.putAll(comparePublicationPoints(rsync, repo, now, threshold));
        }
        return diffs;
    }

    public Map<String, Set<FileEntry>> getRrdpDiff(Instant now, Duration threshold) {
        // Update the main RRDP repository once
        rrdp.update(now, repositoryObjects.getObjects(rrdp.getUrl()));

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (var secondary : appConfig.getRrdpConfig().getOtherUrls().entrySet()) {
            var tag = secondary.getKey();
            var url = secondary.getValue();
            var repo = RepositoryTracker.with(tag, url, now, repositoryObjects.getObjects(url));
            diffs.putAll(comparePublicationPoints(rrdp, repo, now, threshold));
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

    public Map<String, Set<FileEntry>> getPublishedObjectsDiff(Instant now, RepositoryTracker... repositories) {
        final var thresholds = new Duration[]{Duration.of(5, MINUTES), Duration.of(15, MINUTES), Duration.of(30, MINUTES)};

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        // n-choose-2
        // It is safe to only generate subsets of size 2 in one order because
        // we calculate the difference in two directions.
        for (int i = 0; i < repositories.length; i++) {
            var lhs = repositories[i];

            var counter = getOrCreateCounter(lhs.getTag());
            counter.set(lhs.size(now));

            for (int j = 0; j < i; j++) {
                var rhs = repositories[j];

                for (var threshold : thresholds) {
                    diffs.putAll(comparePublicationPoints(lhs, rhs, now, threshold));
                }
            }
        }
        return diffs;
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
    private final String tag;
    private final String url;
    private Map<String, Pair<FileEntry, Instant>> objects;

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
        this.objects = objects;
    }

    /**
     * Update the repository <b>in-place</b> with the given entries at time <i>t</i>.
     */
    public <T extends HasHashAndUri> void update(Instant t, Collection<T> entries) {
        var newObjects = entries.stream()
                .map(x -> Pair.of(FileEntry.from(x), firstSeen(x.getSha256(), t)))
                .collect(Collectors.toUnmodifiableMap(x -> x.getLeft().getSha256(), Function.identity()));
        synchronized (this) {
            objects = newObjects;
        }
    }

    /**
     * Get the (non-associative) difference between this and the other repository
     * at time <i>t</i>, not exceeding threshold.
     */
    public Set<FileEntry> difference(RepositoryTracker other, Instant t, Duration threshold) {
        return Sets.difference(entries(t.minus(threshold)), other.entries(t));
    }

    /**
     * Get the number of objects in this repository, at time <i>t</i>.
     */
    public int size(Instant t) {
        return entries(t).size();
    }

    private Set<FileEntry> entries(Instant t) {
        Collection<Pair<FileEntry, Instant>> allEntries;
        synchronized (this) {
            allEntries = objects.values();
        }
        return allEntries.stream()
                .filter(x -> x.getRight().compareTo(t) <= 0)
                .map(Pair::getLeft).collect(Collectors.toSet());
    }

    private Instant firstSeen(String sha256, Instant now) {
        Pair<?, Instant> previous;
        synchronized (this) {
            previous = objects.get(sha256);
        }
        return previous != null ? previous.getRight() : now;
    }

    public String getTag() {
        return tag;
    }

    public String getUrl() {
        return url;
    }
}
