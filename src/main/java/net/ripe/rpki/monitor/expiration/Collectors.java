package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.RrdpConfig;
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
    private final AppConfig config;
    private final RepositoriesState repositoriesState;

    @Autowired
    public Collectors(CollectorUpdateMetrics metrics,
                      RepositoriesState repositoriesState,
                      AppConfig config) {
        this.metrics = metrics;
        this.repositoriesState = repositoriesState;
        this.config = config;
    }

    // Create fetcher for all rsync repositories that are in the config.
    // The "main" one is the the on-premise repository. On top of it, we
    // can have potentially arbitrary number of cloud-hosted repositories.
    public List<ObjectAndDateCollector> getRsyncCollectors() {
        return config.getRsyncConfig().getTargets().stream().map(
                target -> makeCollector(createRsyncFetcher(target.name(), target.url()))
        ).collect(toList());
    }

    public List<ObjectAndDateCollector> getRrdpCollectors() {
        return config.getRrdpConfig().getTargets().stream().map(
                rrdpTarget -> makeCollector(createRrdpFetcher(rrdpTarget))
        ).collect(toList());
    }

    private ObjectAndDateCollector makeCollector(RepoFetcher fetcher) {
        return new ObjectAndDateCollector(fetcher, metrics, repositoriesState);
    }

    private RepoFetcher createRsyncFetcher(String name, String url) {
        return new RsyncFetcher(config.getRsyncConfig(), name, url);
    }

    private RrdpFetcher createRrdpFetcher(RrdpConfig.RrdpRepositoryConfig rrdpTarget) {
        return new RrdpFetcher(rrdpTarget, config.getProperties());
    }
}
