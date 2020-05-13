package net.ripe.rpki.monitor.publishing;

import lombok.Setter;
import net.ripe.rpki.monitor.service.core.CoreClient;
import net.ripe.rpki.monitor.service.core.dto.PublishedObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Setter
@RestController
public class PublishedObjectStatus {
    @Autowired
    private CoreClient rpkiCoreClient;

    @Autowired
    private PublishedObjectsSummary publishedObjectsSummary;

    @GetMapping("/published-object-analysis")
    public List<PublishedObject> publishedObjectAnalysis() {
        return rpkiCoreClient.publishedObjects();
    }

    @GetMapping("/published-object-diffs")
    public PublishedObjectsSummary.PublicationDiff publishedObjectDiffs() {
        return publishedObjectsSummary.getPublishedObjectsDiff();
    }
}
