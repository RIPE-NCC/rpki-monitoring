package net.ripe.rpki.monitor.repositories;

import net.ripe.rpki.monitor.HasHashAndUri;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stateful representation of the repositories that are monitored.
 */
public class RepositoriesState {
    private final List<RepositoryTracker> repositories;
    private final AtomicReference<List<Consumer<RepositoryTracker>>> updateHooks = new AtomicReference<>(List.of());

    /**
     * Create the initial state from the given repository config. The config
     * defines the repositories as a pair of tag and url, respectively.
     */
    public static RepositoriesState init(Collection<Pair<String, String>> config) {
        var repos = config.stream().map(x -> RepositoryTracker.empty(x.getLeft(), x.getRight())).collect(Collectors.toList());

        return new RepositoriesState(repos);
    }

    RepositoriesState(List<RepositoryTracker> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    public Optional<RepositoryTracker> getTrackerByTag(String tag) {
        return repositories.stream().filter(x -> tag.equals(x.getTag())).findFirst();
    }

    public Optional<RepositoryTracker> getTrackerByUrl(String url) {
        return repositories.stream().filter(x -> url.equals(x.getUrl())).findFirst();
    }

    public <T extends HasHashAndUri> RepositoryTracker updateByTag(String tag, Instant t, Collection<T> entries) {
        var tracker = getTrackerByTag(tag).orElseThrow(() -> new IllegalArgumentException("No tracked repository for tag: " + tag));
        update(tracker, t, entries);
        return tracker;
    }

    public <T extends HasHashAndUri> RepositoryTracker updateByUrl(String url, Instant t, Collection<T> entries) {
        var tracker = getTrackerByUrl(url).orElseThrow(() -> new IllegalArgumentException("No tracked repository for URL: " + url));
        update(tracker, t, entries);
        return tracker;
    }

    private <T extends HasHashAndUri> void update(RepositoryTracker tracker, Instant t, Collection<T> entries) {
        tracker.update(t, entries);
        updateHooks.get().forEach(f -> f.accept(tracker));
    }

    public void addHook(Consumer<RepositoryTracker> f) {
        updateHooks.set(append(updateHooks.get(), f));
    }

    private <T> List<T> append(List<T> xs, T x) {
        return Stream.concat(
                xs.stream(),
                Stream.of(x)
        ).collect(Collectors.toList());
    }
}
