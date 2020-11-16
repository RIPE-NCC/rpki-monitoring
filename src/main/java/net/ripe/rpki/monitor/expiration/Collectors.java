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
        ObjectAndDateCollector mainRsyncCollector = new ObjectAndDateCollector(
            getRsyncFetcher(config.getRsyncConfig().getOnPremiseUrl()),
            metrics,
            repositoryObjects);

        return Stream.concat(
            Stream.of(mainRsyncCollector),
            config.getRsyncConfig().getAwsUrl()
                .stream()
                .map(url ->
                    new ObjectAndDateCollector(
                        getRsyncFetcher(url),
                        metrics,
                        repositoryObjects)
                ))
            .collect(java.util.stream.Collectors.toList());
    }

    private RepoFetcher getRsyncFetcher(String rsyncUrl) {
        return new RsyncFetcher(rsyncUrl, config.getRsyncConfig().getTimeout());
    }

    public ObjectAndDateCollector getRrdpCollector() {
        final RrdpFetcher rrdpFetcher = new RrdpFetcher(config.getRrdpConfig().getUrl(), config.getRestTemplate());
        return new ObjectAndDateCollector(rrdpFetcher, metrics, repositoryObjects);
    }

}
