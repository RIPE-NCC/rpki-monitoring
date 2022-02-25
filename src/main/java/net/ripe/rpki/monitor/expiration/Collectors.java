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
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

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
        return Stream.concat(
            Stream.of(createDefaultRsyncCollector()),
            createOtherUrlsCollectors(config.getRsyncConfig().getOtherUrls(), this::createRsyncFetcher)
        ).collect(toList());
    }

    public List<ObjectAndDateCollector> getRrdpCollectors() {
        return config.getRrdpConfig().getTargets().stream().map(
                rrdpTarget -> new ObjectAndDateCollector(createRrdpFetcher(rrdpTarget), metrics, repositoriesState)
        ).collect(toList());
    }

    private Stream<ObjectAndDateCollector> createOtherUrlsCollectors(final Map<String, String> otherUrls, BiFunction<String, String, RepoFetcher> creator) {
        return otherUrls.entrySet()
            .stream()
            .map(e ->
                new ObjectAndDateCollector(
                    creator.apply(e.getKey(), e.getValue()),
                    metrics,
                    repositoriesState)
            );
    }

    private RepoFetcher createRsyncFetcher(String name, String url) {
        return new RsyncFetcher(config.getRsyncConfig(), name, url);
    }

    private RrdpFetcher createRrdpFetcher(RrdpConfig.RrdpRepositoryConfig rrdpTarget) {
        return new RrdpFetcher(rrdpTarget, config.getProperties());
    }

    ObjectAndDateCollector createDefaultRsyncCollector() {
        return new ObjectAndDateCollector(
            createRsyncFetcher("main", config.getRsyncConfig().getMainUrl()),
            metrics,
            repositoriesState
        );
    }

}
