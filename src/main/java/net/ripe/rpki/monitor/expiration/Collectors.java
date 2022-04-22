package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
public class Collectors {

    private final CollectorUpdateMetrics metrics;
    private final RepositoriesState repositoriesState;

    private final List<ObjectAndDateCollector> rrdpCollectors;
    private final List<ObjectAndDateCollector> rsyncCollectors;

    @Autowired
    public Collectors(CollectorUpdateMetrics metrics,
                      RepositoriesState repositoriesState,
                      AppConfig config) {
        this.metrics = metrics;
        this.repositoriesState = repositoriesState;

        this.rrdpCollectors = config.getRrdpConfig().getTargets().stream().map(
                target -> makeCollector(new RrdpFetcher(target, config.getProperties()))
        ).collect(toList());
        this.rsyncCollectors = config.getRsyncConfig().getTargets().stream().map(
                target -> makeCollector(new RsyncFetcher(config.getRsyncConfig(), target.name(), target.url()))
        ).collect(toList());
    }

    public List<ObjectAndDateCollector> getRsyncCollectors() {
        return this.rsyncCollectors;
    }

    public List<ObjectAndDateCollector> getRrdpCollectors() {
        return this.rrdpCollectors;
    }

    private ObjectAndDateCollector makeCollector(RepoFetcher fetcher) {
        return new ObjectAndDateCollector(fetcher, metrics, repositoriesState);
    }
}
