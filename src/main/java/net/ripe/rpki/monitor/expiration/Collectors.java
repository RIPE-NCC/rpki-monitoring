package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Component
public class Collectors {

    private final CollectorUpdateMetrics metrics;
    private final RepositoryObjects repositoryObjects;
    private final AppConfig config;

    @Autowired
    public Collectors(CollectorUpdateMetrics metrics,
                      RepositoryObjects repositoryObjects,
                      AppConfig config) {
        this.metrics = metrics;
        this.repositoryObjects = repositoryObjects;
        this.config = config;
    }

    // Create fetcher for all rsync repositories that are in the config.
    // The "main" one is the the on-premise repository. On top of it, we
    // can have potentially arbitrary number of cloud-hosted repositories.
    public List<ObjectAndDateCollector> getRsyncCollectors() {
        return Stream.concat(
            Stream.of(createDefaultRsyncCollector()),
            config.getRsyncConfig().getOtherUrls().entrySet()
                .stream()
                .map(e ->
                    new ObjectAndDateCollector(
                        createRsyncFetcher(e.getKey(), e.getValue()),
                        metrics,
                        repositoryObjects)
                ))
            .collect(toList());
    }

    // Create fetcher for all rsync repositories that are in the config.
    // The "main" one is the the on-premise repository. On top of it, we
    // can have potentially arbitrary number of cloud-hosted repositories.
    public List<ObjectAndDateCollector> getRrdpCollectors() {
        return Stream.concat(
            Stream.of(createDefaultRrdpCollector()),
            config.getRrdpConfig().getOtherUrls().entrySet()
                .stream()
                .map(e ->
                    new ObjectAndDateCollector(
                        createRrdpCollector(e.getKey(), e.getValue()),
                        metrics,
                        repositoryObjects)
                ))
            .collect(toList());
    }

    private RepoFetcher createRsyncFetcher(String name, String rsyncUrl) {
        return new RsyncFetcher(name, rsyncUrl, config.getRsyncConfig().getTimeout());
    }

    private RrdpFetcher createRrdpCollector(String name, String url) {
        return new RrdpFetcher(name, url, config.getRestTemplate());
    }

    ObjectAndDateCollector createDefaultRrdpCollector() {
        return new ObjectAndDateCollector(
            createRrdpCollector("main", config.getRrdpConfig().getMainUrl()),
            metrics,
            repositoryObjects);
    }

    ObjectAndDateCollector createDefaultRsyncCollector() {
        return new ObjectAndDateCollector(
            createRsyncFetcher("main", config.getRsyncConfig().getMainUrl()),
            metrics,
            repositoryObjects);
    }

}
