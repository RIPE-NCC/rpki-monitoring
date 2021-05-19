package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            createOtherUrlsCollectors(config.getRsyncConfig().getOtherUrls(), this::createRsyncFetcher)
        ).collect(toList());
    }

    public List<ObjectAndDateCollector> getRrdpCollectors() {
        return Stream.concat(
            Stream.of(createDefaultRrdpCollector()),
            createOtherUrlsCollectors(config.getRrdpConfig().getOtherUrls(), this::createRrdpFetcher)
        ).collect(toList());
    }

    private Stream<ObjectAndDateCollector> createOtherUrlsCollectors(final Map<String, String> otherUrls, Function<Map.Entry<String, String>, RepoFetcher> creator) {
        return otherUrls.entrySet()
            .stream()
            .map(e ->
                new ObjectAndDateCollector(
                    creator.apply(e),
                    metrics,
                    repositoryObjects)
            );
    }

    private RepoFetcher createRsyncFetcher(Map.Entry<String, String> entry) {
        return new RsyncFetcher(entry.getKey(), entry.getValue(), config.getRsyncConfig().getTimeout());
    }

    private RrdpFetcher createRrdpFetcher(Map.Entry<String, String> entry) {
        return new RrdpFetcher(entry.getKey(), entry.getValue(), config.getRestTemplate());
    }

    ObjectAndDateCollector createDefaultRrdpCollector() {
        return new ObjectAndDateCollector(
            createRrdpFetcher(Map.entry("main", config.getRrdpConfig().getMainUrl())),
            metrics,
            repositoryObjects);
    }

    ObjectAndDateCollector createDefaultRsyncCollector() {
        return new ObjectAndDateCollector(
            createRsyncFetcher(Map.entry("main", config.getRsyncConfig().getMainUrl())),
            metrics,
            repositoryObjects);
    }

}
