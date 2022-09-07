package net.ripe.rpki.monitor.publishing;

import com.google.common.base.Objects;
import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class PublishedObjectStatusController {
    private final PublishedObjectsSummaryService publishedObjectsSummaryService;
    private final RepositoriesState repositories;

    @GetMapping("/published-object-diffs")
    public Map<PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>> publishedObjectDiffs() {
        var now = Instant.now();
        return publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(now, repositories.allTrackers());
    }

    @GetMapping("/diff")
    public Set<RepositoryEntry> diff(
            @RequestParam("lhs") String lhs,
            @RequestParam("rhs") String rhs,
            @RequestParam(name = "threshold", defaultValue = "0") int threshold
    ) {
        var lhsTracker = repositories.getTrackerByTag(lhs).orElseThrow(() -> new IllegalArgumentException("No such repository tracker: " + lhs));
        var rhsTracker = repositories.getTrackerByTag(rhs).orElseThrow(() -> new IllegalArgumentException("No such repository tracker: " + lhs));

        return lhsTracker.difference(rhsTracker, Instant.now(), Duration.ofSeconds(threshold));
    }

    @GetMapping("/{repository}/info")
    public Optional<RepositoryInfo> getInfo(
            @PathVariable("repository") String repository,
            @RequestParam(name = "threshold", defaultValue = "0") int threshold
    ) {
        var t = Instant.now().minusSeconds(threshold);
        return repositories.getTrackerByTag(repository)
                .map(repo -> new RepositoryInfo(repo.getTag(), repo.getUrl(), repo.getType().name(), repo.view(t).size()));
    }
    record RepositoryInfo(String tag, String url, String type, long size) {}

    @GetMapping("/{repository}/objects")
    public Optional<Set<RepositoryEntry>> getObject(
            @PathVariable("repository") String repository,
            @RequestParam("uri") String uri,
            @RequestParam(name = "threshold", defaultValue = "0") int threshold
    ) {
        var t = Instant.now().minusSeconds(threshold);
        return repositories.getTrackerByTag(repository).map(
                repo -> repo.view(t).entries().filter(x -> Objects.equal(uri, x.getUri())).collect(toSet())
        );
    }

    @GetMapping("/{repository}/inspect")
    public Optional<Set<RepositoryTracker.TrackedObject>> inspectObject(
            @PathVariable("repository") String repository,
            @RequestParam("uri") String uri
    ) {
        return repositories.getTrackerByTag(repository)
                .map(repo -> repo.inspect(uri));
    }

    @GetMapping("/repositories")
    public List<RepositoryTracker> repositories() {
        return repositories.allTrackers();
    }

    @GetMapping("/rsync-diffs")
    public Map<PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>> rsyncDiff() {
        return publishedObjectsSummaryService.getDiff(
                Instant.now(),
                repositories.trackersOfType(RepositoryTracker.Type.CORE),
                repositories.trackersOfType(RepositoryTracker.Type.RSYNC)
        );
    }

    @GetMapping("/rrdp-diffs")
    public Map<PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>> rrdpDiff() {
        return publishedObjectsSummaryService.getDiff(
                Instant.now(),
                repositories.trackersOfType(RepositoryTracker.Type.CORE),
                repositories.trackersOfType(RepositoryTracker.Type.RRDP)
        );
    }
}
