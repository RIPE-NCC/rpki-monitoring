package net.ripe.rpki.monitor.expiration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

@Controller
@RequestMapping("/about_to_expire")
public class ObjectsAboutToExpireController {

    final SummaryService summaryService;

    @Autowired
    public ObjectsAboutToExpireController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping(value = "rrdp", produces = "application/json")
    @ResponseBody
    public Set<RepoObject> rrdpSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return summaryService.getRrdpObjectsAboutToExpire(inHours);
    }

    @GetMapping(value = "rsync", produces = "application/json")
    @ResponseBody
    public Set<RepoObject> rsyncSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return summaryService.getRsyncObjectsAboutToExpire(inHours);
    }

}
