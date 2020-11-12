package net.ripe.rpki.monitor.compare;

import lombok.Setter;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@Setter
@RestController
public class RsyncComparisonController {
    @Autowired
    private RsyncComparisonService rsyncComparisonService;

    @GetMapping("/blabla")
    public RsyncComparisonService.RsyncDiff publishedObjectDiffs() {
        return null;
//        return rsyncComparisonService.updateRsyncComparisonMetrics();
    }
}
