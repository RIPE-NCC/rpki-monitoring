package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/about_to_expire")
public class ObjectsAboutToExpireController {

    private final RepositoryObjects repositoryObjects;
    private final AppConfig appConfig;

    @Autowired
    public ObjectsAboutToExpireController(RepositoryObjects repositoryObjects, AppConfig appConfig) {
        this.repositoryObjects = repositoryObjects;
        this.appConfig = appConfig;
    }

    @GetMapping(value = "rrdp")
    public Set<RepoObject> rrdpSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return repositoryObjects.geRepositoryObjectsAboutToExpire(appConfig.getRrdpUrl(), inHours);
    }

    @GetMapping(value = "rsync")
    public Set<RepoObject> rsyncSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        return repositoryObjects.geRepositoryObjectsAboutToExpire(appConfig.getRsyncUrl(), inHours);
    }

}
