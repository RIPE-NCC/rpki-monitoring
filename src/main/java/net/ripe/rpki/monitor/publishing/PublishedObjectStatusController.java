package net.ripe.rpki.monitor.publishing;

import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class PublishedObjectStatusController {
    private final PublishedObjectsSummaryService publishedObjectsSummaryService;
    private final RepositoriesState repositories;

    @GetMapping("/published-object-diffs")
    public Map<String, Set<RepositoryEntry>> publishedObjectDiffs() {
        var now = Instant.now();
        return publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(now, repositories.allTrackers());
    }

    @GetMapping("/diff")
    public Set<RepositoryEntry> diff(
            @RequestParam("lhs") String lhs,
            @RequestParam("rhs") String rhs,
            @RequestParam(name = "threshold", defaultValue = "300") int threshold
    ) {
        var lhsTracker = repositories.getTrackerByTag(lhs).orElseThrow(() -> new IllegalArgumentException("No such repository tracker: " + lhs));
        var rhsTracker = repositories.getTrackerByTag(rhs).orElseThrow(() -> new IllegalArgumentException("No such repository tracker: " + lhs));

        return lhsTracker.difference(rhsTracker, Instant.now(), Duration.ofSeconds(threshold));
    }

    @GetMapping("/rsync-diffs")
    public Map<String, Set<RepositoryEntry>> rsyncDiff() {
        return publishedObjectsSummaryService.getDiff(
                Instant.now(),
                repositories.trackersOfType(RepositoryTracker.Type.CORE),
                repositories.trackersOfType(RepositoryTracker.Type.RSYNC)
        );
    }

    @GetMapping("/rrdp-diffs")
    public Map<String, Set<RepositoryEntry>> rrdpDiff() {
        return publishedObjectsSummaryService.getDiff(
                Instant.now(),
                repositories.trackersOfType(RepositoryTracker.Type.CORE),
                repositories.trackersOfType(RepositoryTracker.Type.RRDP)
        );
    }
}
