package net.ripe.rpki.monitor.expiration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/about_to_expire")
public class ObjectsAboutToExpireController {

    final SummaryService summaryService;

    @Autowired
    public ObjectsAboutToExpireController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping(value = "rrdp")
    public Set<RepoObject> rrdpSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return summaryService.getRrdpObjectsAboutToExpire(inHours);
    }

    @GetMapping(value = "rsync")
    public Set<RepoObject> rsyncSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return summaryService.getRsyncObjectsAboutToExpire(inHours);
    }

}
