package net.ripe.rpki.monitor.repositories;

import com.google.common.collect.Sets;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Getter
    private final Type type;

    public enum Type {
        CORE, RRDP, RSYNC
    }

    private final AtomicReference<Map<String, Pair<RepositoryEntry, Instant>>> objects;

    /**
     * Get an empty repository.
     */
    public static RepositoryTracker empty(String tag, String url, Type type) {
        return new RepositoryTracker(tag, url, type, Collections.emptyMap());
    }

    /**
     * Create a repository with the given entries and time <i>t</i>.
     */
    public static RepositoryTracker with(String tag, String url, Type type, Instant t, Stream<RepositoryEntry> entries) {
        var repo = RepositoryTracker.empty(tag, url, type);
        repo.update(t, entries);
        return repo;
    }

    private RepositoryTracker(String tag, String url, Type type, Map<String, Pair<RepositoryEntry, Instant>> objects) {
        this.tag = tag;
        this.url = url;
        this.type = type;
        this.objects = new AtomicReference<>(objects);
    }

    /**
     * Update this repository with the given entries at time <i>t</i>.
     * <p>
     * The repository's objects are <b>replaced</b> by the entries. Any objects
     * no longer present in the <code>entries</code> set are discarded from the
     * repository.
     */
    public void update(Instant t, Stream<RepositoryEntry> entries) {
        var newObjects = entries
                .map(x -> Pair.of(x, firstSeenAt(x.getSha256(), t)))
                .collect(Collectors.toUnmodifiableMap(x -> x.getLeft().getSha256(), Function.identity()));
        objects.set(newObjects);
    }

    /**
     * Get the (non-associative) difference between this and the other repository
     * at time <i>t</i>, not exceeding threshold.
     */
    public Set<RepositoryEntry> difference(RepositoryTracker other, Instant t, Duration threshold) {
        return Sets.difference(entriesAt(t.minus(threshold)), other.entriesAt(t));
    }

    /**
     * Get the number of objects in this repository, at time <i>t</i>.
     */
    public int size(Instant t) {
        return entriesAt(t).size();
    }

    /**
     * Get the objects that are expired at time <i>t</i>. Object that have no
     * expiration are considered open-ended (i.e. never to expire.
     */
    public Set<RepositoryEntry> expirationBefore(Instant t) {
        return entriesAt(t).stream()
                .filter(x -> x.getExpiration().map(expiration -> expiration.compareTo(t) < 0).orElse(false))
                .collect(Collectors.toSet());
    }

    /**
     * Get all entries in the repository present at time <i>t</i>.
     * <p>
     * Presence is defined as objects first seen before (or at) a given time.
     * I.e. any objects first seen after <i>t</i> are considered not present.
     */
    public Set<RepositoryEntry> entriesAt(Instant t) {
        return objects.get().values().stream()
                .filter(x -> x.getRight().compareTo(t) <= 0)
                .map(Pair::getLeft).collect(Collectors.toSet());
    }

    private Instant firstSeenAt(String sha256, Instant now) {
        var previous = objects.get().get(sha256);
        return previous != null ? previous.getRight() : now;
    }
}
