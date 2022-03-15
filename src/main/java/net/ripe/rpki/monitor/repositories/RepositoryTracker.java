package net.ripe.rpki.monitor.repositories;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;

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

    // Stores the repository objects index by their key (sha256 * uri)
    private final AtomicReference<Map<Long, TrackedObject>> objects;

    public enum Type {
        CORE, RRDP, RSYNC
    }

    public record TrackedObject(RepositoryEntry entry, Instant firstSeen, Optional<Instant> disposedAt) {
        public static TrackedObject of(RepositoryEntry entry, Instant firstSeen) {
            return new TrackedObject(entry, firstSeen, Optional.empty());
        }

        public static long key(String sha256, String uri) {
            return (long) sha256.hashCode() * (long) uri.hashCode();
        }

        public TrackedObject dispose(Instant t) {
            return new TrackedObject(entry, firstSeen, Optional.of(t));
        }

        public long key() {
            return key(entry.getSha256(), entry.getUri());
        }
    }

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
        var newObjects = entries
                .map(x -> TrackedObject.of(x, firstSeenAt(x.getSha256(), x.getUri(), t)))
                .collect(toMap(TrackedObject::key, Function.identity()));
        var disposed = this.objects.get().values().stream()
                .filter(x -> x.disposedAt.map(disposedAt -> disposedAt.isAfter(t.minus(gracePeriod))).orElse(true))
                .filter(x -> ! newObjects.containsKey(x.key()))
                .map(x -> x.dispose(t))
                .collect(toUnmodifiableMap(TrackedObject::key, Function.identity()));

        newObjects.putAll(disposed);
        this.objects.set(Collections.unmodifiableMap(newObjects));
    }

    /**
     * Get the (non-commutative) difference between this and the other repository
     * at time <i>t</i>, not exceeding threshold.
     *
     * For all non-disposed objects first seen before or at time <i>t - threshold</i>
     * in this repository, the other repository is expected to have the same object
     * first seen before or at time <i>t</i> or have the object disposed before
     * time <i>t - threshold</i>.
     */
    public Set<RepositoryEntry> difference(RepositoryTracker other, Instant t, Duration threshold) {
        var lhs = new View(objects.get(), Predicates.firstSeenBefore(t.minus(threshold)).and(Predicates.nonDisposed()));
        var rhs = new View(other.objects.get(), Predicates.firstSeenBefore(t).and(Predicates.notDisposedAt(t.minus(threshold))));
        return lhs.entries()
                .filter(Predicate.not(rhs::hasObject))
                .collect(toSet());
    }

    /**
     * Get a view on the repository objects present at time <i>t</i>.
     *
     * Presence of an object is defined as first-seen before (or at) time <i>t</i> and
     * not disposed or disposed after time <i>t</i>.
`     */
    public View view(Instant t) {
        return new View(objects.get(), Predicates.firstSeenBefore(t).and(Predicates.notDisposedAt(t)));
    }

    /**
     * Inspect <i>all</i> objects at the given uri. The objects may not match the
     *
     * Returning <code>TrackedObject</code> allows to inspect timings as well
     * as the object data.
     */
    public Set<TrackedObject> inspect(String uri) {
        return objects.get().values().stream()
                .filter(x -> Objects.equals(uri, x.entry.getUri()))
                .collect(toSet());
    }

    private Instant firstSeenAt(String sha256, String uri, Instant now) {
        var previous = objects.get().get(TrackedObject.key(sha256, uri));
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
            Map<Long, TrackedObject> objects,
            Predicate<TrackedObject> filter
    ) {
        /**
         * Get the repository entry with the given hash, or nothing if the repository
         * does not have such object.
         */
        public Optional<RepositoryEntry> getObject(String sha256, String uri) {
            return Optional.ofNullable(objects.get(TrackedObject.key(sha256, uri)))
                    .filter(filter)
                    .map(TrackedObject::entry);
        }

        /**
         * Test if the repository has an object with the given object's hash and URI.
         *
         * Semantically objects in a repository would be present by just verifying
         * their hash. However, semantics are not checked here. Hence, for the purpose
         * of monitoring, two identical objects are considered different when at
         * different paths in the repository.
         */
        public boolean hasObject(RepositoryEntry object) {
            return getObject(object.getSha256(), object.getUri()).isPresent();
        }

        /**
         * Get the objects that are expired at time <i>t</i>. Objects that have no
         * expiration are considered open-ended (i.e. never to expire).
         */
        public Set<RepositoryEntry> expiration(Instant t) {
            return entries()
                    .filter(x -> x.getExpiration().map(expiration -> expiration.compareTo(t) < 0).orElse(false))
                    .collect(toSet());
        }

        public long size() {
            return entries().count();
        }

        public Stream<RepositoryEntry> entries() {
            return objects.values().stream()
                    .filter(filter)
                    .map(TrackedObject::entry);
        }
    }
    /**
     * Standard predicates on <code>TrackedObject</code>s.
     */
    public interface Predicates {
        /**
         * Matches objects first seen before ar at time <i>t</i>.
         */
        static Predicate<TrackedObject> firstSeenBefore(Instant t) {
            return x -> x.firstSeen.compareTo(t) <= 0;
        }

        /**
         * Matches objects disposed before ar at time <i>t</i>. Objects not
         * disposed don't match.
         */
        static Predicate<TrackedObject> disposedBefore(Instant t) {
            return x -> x.disposedAt.map(y -> y.compareTo(t) <= 0).orElse(false);
        }

        /**
         * Negation of <code>disposedBefore</code>.
         */
        static Predicate<TrackedObject> notDisposedAt(Instant t) {
            return disposedBefore(t).negate();
        }

        /**
         * Matches objects that are not at all disposed.
         */
        static Predicate<TrackedObject> nonDisposed() {
            return x -> x.disposedAt.isEmpty();
        }
    }
}
