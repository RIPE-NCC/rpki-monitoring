package net.ripe.rpki.monitor.publishing;

import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class PublishedObjectStatusController {
    private final PublishedObjectsSummaryService publishedObjectsSummaryService;
    private final RepositoriesState repositoriesState;

    @GetMapping("/published-object-diffs")
    public Map<String, Set<FileEntry>> publishedObjectDiffs() {
        var now = Instant.now();
        return publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(now, repositoriesState.allTrackers());
    }

    @GetMapping("/rsync-diffs")
    public Map<String, Set<FileEntry>> rsyncDiff() {
        return publishedObjectsSummaryService.getRsyncDiff(Instant.now(), Duration.ofSeconds(300));
    }

    @GetMapping("/rrdp-diffs")
    public Map<String, Set<FileEntry>> rrdpDiff() {
        return publishedObjectsSummaryService.getRrdpDiff(Instant.now(), Duration.ofSeconds(300));
    }
}
