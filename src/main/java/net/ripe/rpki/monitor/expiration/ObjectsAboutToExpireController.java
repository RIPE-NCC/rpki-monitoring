package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/about_to_expire")
public class ObjectsAboutToExpireController {

    private final RepositoriesState repositories;
    private final AppConfig appConfig;

    @Autowired
    public ObjectsAboutToExpireController(RepositoriesState repositories, AppConfig appConfig) {
        this.repositories = repositories;
        this.appConfig = appConfig;
    }

    @GetMapping(value = "rrdp")
    public Set<RepositoryEntry> rrdpSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        var repository = repositories.getTrackerByUrl(appConfig.getRrdpConfig().getMainUrl())
                .orElseThrow(() -> new IllegalStateException("No tracker for RRDP main repository"));
        return repository.expirationBefore(Instant.now().plusSeconds(3600L * inHours));
    }

    @GetMapping(value = "rsync")
    public Set<RepositoryEntry> rsyncSummary(@RequestParam(value = "in_hours", defaultValue = "2") int inHours) {
        var repository = repositories.getTrackerByUrl(appConfig.getRsyncConfig().getMainUrl())
                .orElseThrow(() -> new IllegalStateException("No tracker for rsync main repository"));
        return repository.expirationBefore(Instant.now().plusSeconds(3600L * inHours));
    }
}
