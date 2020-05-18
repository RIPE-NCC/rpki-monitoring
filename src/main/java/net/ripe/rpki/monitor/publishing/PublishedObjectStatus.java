package net.ripe.rpki.monitor.publishing;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@Setter
@RestController
public class PublishedObjectStatus {
    @Autowired
    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    @GetMapping("/published-object-diffs")
    public PublishedObjectsSummaryService.PublicationDiff publishedObjectDiffs() {
        return publishedObjectsSummaryService.getPublishedObjectsDiff();
    }
}
