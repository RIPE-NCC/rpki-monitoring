package net.ripe.rpki.monitor.repositories;

import com.google.common.collect.Sets;
import lombok.Getter;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Track lifetime of objects in a single repository.
 * <p>
 * More specifically, for each object the time its first seen in the repository
 * is tracked. This allows tracking differences in repositories with temporal
 * thresholds, since an object won't reach every repository at exactly the same
 * time.
 * <p>
 * An instance of this class can be safely shared across threads.
 */
public class RepositoryTracker {
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
     * <p>
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
     * <p>
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
