package net.ripe.rpki.monitor.repositories;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
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

    // Stores the repository objects index by their sha256 hash
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
        return previous != null ? previous.getRight() : now;
    }

    /**
     * Represents a view on the contents of a repository at a specific time.
     *
     * More specifically, objects first seen after that time are considered out
     * of scope from this view. Objects that are now gone from the repository
     * are not brought back.
     */
    public static class View {
        private final Map<String, Pair<RepositoryEntry, Instant>> objects;
        private final Instant time;

        private View(Map<String, Pair<RepositoryEntry, Instant>> objects, Instant time) {
            this.objects = objects;
            this.time = time;
        }

        /**
         * Get the repository entry with the given hash, or nothing if the repository
         * does not have such object.
         */
        public Optional<RepositoryEntry> getObject(String sha256) {
            return Optional.ofNullable(objects.get(sha256))
                    .filter(this::inScope)
                    .map(Pair::getLeft);
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
                    .map(Pair::getLeft);
        }

        private boolean inScope(Pair<RepositoryEntry, Instant> x) {
            return x.getRight().compareTo(time) <= 0;
        }
    }
}
