package net.ripe.rpki.monitor.publishing;

import lombok.Setter;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;


@Setter
@RestController
public class PublishedObjectStatusController {
    @Autowired
    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    @GetMapping("/published-object-diffs")
    public Map<String, Set<FileEntry>> publishedObjectDiffs() {
        return publishedObjectsSummaryService.getPublishedObjectsDiff();
    }

    @GetMapping("/rsync-diffs")
    public Map<String, Set<FileEntry>> rsyncDiff() {
        return publishedObjectsSummaryService.getRsyncDiff();
    }

    @GetMapping("/rrdp-diffs")
    public Map<String, Set<FileEntry>> rrdpDiff() {
        return publishedObjectsSummaryService.getRrdpDiff();
    }
}
