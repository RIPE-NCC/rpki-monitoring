package net.ripe.rpki.monitor.repositories;

import lombok.Getter;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;

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
    /** repository tag: rsync, core, main, cdn1, cdn2, ... */
    @Getter
    private final String tag;
    @Getter
    private final String url;
    @Getter
    private final Type type;

    // Time to keep disposed objects around
    private final Duration gracePeriod;

    // Stores the repository objects index by their key (sha256 and uri)
    private final AtomicReference<Map<TrackedObject.Key, TrackedObject>> objects;

    public enum Type {
        CORE, RRDP, RSYNC
    }

    public record TrackedObject(RepositoryEntry entry, Instant firstSeen, Optional<Instant> disposedAt) {
        public record Key(byte[] sha256, @Getter String uri) implements HasHashAndUri {
            @Override
            public int hashCode() {
                int result = Objects.hash(uri);
                result = 31 * result + Arrays.hashCode(sha256);
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                return switch (obj) {
                    case Key key -> {
                        yield Arrays.equals(sha256, key.sha256) && uri.equals(key.uri);
                    }
                    default -> {
                        yield false;
                    }
                };
            }
        }

        public static TrackedObject of(RepositoryEntry entry, Instant firstSeen) {
            return new TrackedObject(entry, firstSeen, Optional.empty());
        }

        public static Key key(byte[] sha256, String uri) {
            return new Key(sha256, uri);
        }

        public TrackedObject dispose(Instant t) {
            return this.disposedAt.isPresent() ? this : new TrackedObject(entry, firstSeen, Optional.of(t));
        }

        public Key key() {
            return key(entry.sha256(), entry.getUri());
        }

        public RepositoryObjectType getObjectType() {
            return RepositoryObjectType.parse(entry.getUri());
        }
    }

    public PublishedObjectsSummaryService.RepositoryKey key() {
        return new PublishedObjectsSummaryService.RepositoryKey(tag, url);
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
    public static RepositoryTracker with(String tag, String url, Type type, Instant t, Stream<RepositoryEntry> entries, Duration gracePeriod) {
        var repo = RepositoryTracker.empty(tag, url, type, gracePeriod);
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
        var threshold = t.minus(gracePeriod);
        this.objects.getAndUpdate((objects) -> {
            var newObjects = entries
                    .map(x -> TrackedObject.of(x, firstSeenAt(objects, x.sha256(), x.getUri(), t)))
                    .collect(toMap(TrackedObject::key, Function.identity()));
            var disposed = objects.values().stream()
                    .filter(x -> ! newObjects.containsKey(x.key()))
                    .filter(x -> x.disposedAt.map(disposedAt -> disposedAt.isAfter(threshold)).orElse(true))
                    .map(x -> x.dispose(t))
                    .collect(toUnmodifiableMap(TrackedObject::key, Function.identity()));

            newObjects.putAll(disposed);
            return Collections.unmodifiableMap(newObjects);
        });
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
     * Same as @difference but also filters by the object type.
     */
    public Set<RepositoryEntry> difference(RepositoryTracker other, Instant t, Duration threshold, RepositoryObjectType objectType) {
        var lhs = new View(objects.get(), Predicates
                .firstSeenBefore(t.minus(threshold))
                .and(Predicates.nonDisposed())
                .and(Predicates.ofType(objectType)));

        var rhs = new View(other.objects.get(), Predicates
                .firstSeenBefore(t)
                .and(Predicates.notDisposedAt(t.minus(threshold)))
                .and(Predicates.ofType(objectType)));

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

    private static Instant firstSeenAt(Map<TrackedObject.Key, TrackedObject> objects, byte[] sha256, String uri, Instant now) {
        var previous = objects.get(TrackedObject.key(sha256, uri));
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
            Map<TrackedObject.Key, TrackedObject> objects,
            Predicate<TrackedObject> filter
    ) {
        /**
         * Get the repository entry with the given hash, or nothing if the repository
         * does not have such object.
         */
        public Optional<RepositoryEntry> getObject(byte[] sha256, String uri) {
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
            return getObject(object.sha256(), object.getUri()).isPresent();
        }

        /**
         * Get the objects that are expired at time <i>t</i>. Objects that have no
         * expiration are considered open-ended (i.e. never to expire).
         */
        public Set<RepositoryEntry> expiration(Instant t) {
            return entries()
                    .filter(x -> x.expiration().map(expiration -> expiration.isBefore(t)).orElse(false))
                    .collect(toSet());
        }

        public long size() {
            return entries().count();
        }

        public Stream<RepositoryEntry> entries() {
            return stream().map(TrackedObject::entry);
        }

        public Stream<TrackedObject> stream() {
            return objects.values().stream().filter(filter);
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


        static Predicate<TrackedObject> ofType(RepositoryObjectType t) {
            return x -> x.getObjectType() == t;
        }
    }
}
