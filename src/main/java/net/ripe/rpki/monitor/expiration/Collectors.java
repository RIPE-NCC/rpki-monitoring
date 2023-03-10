package net.ripe.rpki.monitor.expiration;

import lombok.Getter;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.util.http.WebClientBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
public class Collectors {

    private final CollectorUpdateMetrics metrics;
    private final RepositoriesState repositoriesState;

    @Getter
    private final List<ObjectAndDateCollector> rrdpCollectors;
    @Getter
    private final List<ObjectAndDateCollector> rsyncCollectors;

    @Autowired
    public Collectors(CollectorUpdateMetrics metrics,
                      RepositoriesState repositoriesState,
                      AppConfig config,
                      FetcherMetrics fetcherMetrics,
                      WebClientBuilderFactory webclientBuilder) {
        this.metrics = metrics;
        this.repositoriesState = repositoriesState;

        this.rrdpCollectors = config.getRrdpConfig().getTargets().stream().map(
                target -> makeCollector(new RrdpFetcher(target, config, fetcherMetrics, webclientBuilder))
        ).collect(toList());
        this.rsyncCollectors = config.getRsyncConfig().getTargets().stream().map(
                target -> makeCollector(new RsyncFetcher(config.getRsyncConfig(), target.name(), target.url(), fetcherMetrics))
        ).collect(toList());
    }

    private ObjectAndDateCollector makeCollector(RepoFetcher fetcher) {
        return new ObjectAndDateCollector(fetcher, metrics, repositoriesState);
    }
}
