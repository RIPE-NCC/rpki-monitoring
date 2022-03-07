package net.ripe.rpki.monitor.repositories;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;

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

    // Time to keep disposed objects around
    private final Duration gracePeriod;

    public enum Type {
        CORE, RRDP, RSYNC
    }

    record TrackedObject(RepositoryEntry entry, Instant firstSeen, Optional<Instant> disposedAt) {
        public static TrackedObject of(RepositoryEntry entry, Instant firstSeen) {
            return new TrackedObject(entry, firstSeen, Optional.empty());
        }

        public TrackedObject dispose(Instant t) {
            return new TrackedObject(entry, firstSeen, Optional.of(t));
        }
    }

    // Stores the repository objects index by their sha256 hash
    private final AtomicReference<Map<String, TrackedObject>> objects;

    /**
     * Get an empty repository.
     */
    public static RepositoryTracker empty(String tag, String url, Type type, Duration gracePeriod) {
        return new RepositoryTracker(tag, url, type, gracePeriod);
    }

    /**
     * Create a repository with the given entries and time <i>t</i>.
     */
    public static RepositoryTracker with(String tag, String url, Type type, Instant t, Stream<RepositoryEntry> entries, Duration gracePersiod) {
        var repo = RepositoryTracker.empty(tag, url, type, gracePersiod);
        repo.update(t, entries);
        return repo;
    }

    private RepositoryTracker(String tag, String url, Type type, Duration gracePeriod) {
        this.tag = tag;
        this.url = url;
        this.type = type;
        this.gracePeriod = gracePeriod;
        this.objects = new AtomicReference<>(emptyMap());
    }

    /**
     * Update this repository with the given entries at time <i>t</i>.
     * <p>
     * The repository's objects are <b>replaced</b> by the entries. Any objects
     * no longer present in the <code>entries</code> set are considered to be
     * discarded from the repository. Discarded objects are kept around until
     * a next update where <code>last-seen < t-gc</code>.
     * <p>
     * Time of updates on the repository must be strictly increasing.
     */
    public void update(Instant t, Stream<RepositoryEntry> entries) {
        var objects = entries
                .map(x -> TrackedObject.of(x, firstSeenAt(x.getSha256(), t)))
                .collect(Collectors.toUnmodifiableMap(x -> x.entry.getSha256(), Function.identity()));
        var disposed = this.objects.get().values().stream()
                .filter(x -> x.disposedAt.map(disposedAt -> disposedAt.isAfter(t.minus(gracePeriod))).orElse(true))
                .filter(x -> ! objects.containsKey(x.entry.getSha256()))
                .collect(Collectors.toUnmodifiableMap(x -> x.entry.getSha256(), x -> x.dispose(t)));

        var newState = new HashMap<>(objects);
        newState.putAll(disposed);
        this.objects.set(newState);
    }

    /**
     * Get the (non-associative) difference between this and the other repository
     * at time <i>t</i>, not exceeding threshold.
     */
    public Set<RepositoryEntry> difference(RepositoryTracker other, Instant t, Duration threshold) {
        var rhs = other.view(t);
        return view(t.minus(threshold)).entries()
                .filter(Predicate.not(rhs::hasObject))
                .collect(Collectors.toSet());
    }

    /**
     * Get a view on the repository objects present at time <i>t</i>.
     *
     * Presence is defined as objects first seen before (or at) a given time.
     * I.e. any objects first seen after <i>t</i> are considered not present.
     */
    public View view(Instant t) {
        return new View(objects.get(), t);
    }

    private Instant firstSeenAt(String sha256, Instant now) {
        var previous = objects.get().get(sha256);
        return previous != null ? previous.firstSeen() : now;
    }

    /**
     * Represents a view on the contents of a repository at a specific time.
     *
     * More specifically, objects first seen after that time are considered out
     * of scope from this view. Objects that are now gone from the repository
     * are not brought back.
     */
    public record View(
            Map<String, TrackedObject> objects,
            Instant time
    ) {
        /**
         * Get the repository entry with the given hash, or nothing if the repository
         * does not have such object.
         */
        public Optional<RepositoryEntry> getObject(String sha256) {
            return Optional.ofNullable(objects.get(sha256))
                    .filter(this::inScope)
                    .map(TrackedObject::entry);
        }

        /**
         * Test if the repository has an object with the given object's hash and URI.
         *
         * Semantically objects in a repository would be present by just verifying
         * their hash. However, semantics are not checked here. Hence for the purpose
         * of monitoring, two identical objects are considered different when at
         * different paths in the repository.
         */
        public boolean hasObject(RepositoryEntry object) {
            return getObject(object.getSha256())
                    .map(x -> Objects.equals(x.getUri(), object.getUri()))
                    .orElse(false);
        }

        /**
         * Get the objects that are expired at time <i>t</i>. Objects that have no
         * expiration are considered open-ended (i.e. never to expire).
         */
        public Set<RepositoryEntry> expiration(Instant t) {
            return entries()
                    .filter(x -> x.getExpiration().map(expiration -> expiration.compareTo(t) < 0).orElse(false))
                    .collect(Collectors.toSet());
        }

        public long size() {
            return entries().count();
        }

        public Stream<RepositoryEntry> entries() {
            return objects.values().stream()
                    .filter(this::inScope)
                    .map(TrackedObject::entry);
        }

        private boolean inScope(TrackedObject x) {
            return x.firstSeen.compareTo(time) <= 0
                && x.disposedAt.map(disposed -> disposed.compareTo(time) > 0).orElse(true);
        }
    }
}
