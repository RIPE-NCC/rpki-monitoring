package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.fetchers.Fetchers;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
public class Collectors {

    private final Fetchers fetchers;
    private final CollectorUpdateMetrics metrics;
    private final RepositoryObjects repositoryObjects;
    private final AppConfig config;

    @Autowired
    public Collectors(Fetchers fetchers,
                      CollectorUpdateMetrics metrics,
                      RepositoryObjects repositoryObjects,
                      AppConfig config) {
        this.fetchers = fetchers;
        this.metrics = metrics;
        this.repositoryObjects = repositoryObjects;
        this.config = config;
    }

    public List<ObjectAndDateCollector> getRsyncCollectors() {
        ObjectAndDateCollector mainRsyncCollector = new ObjectAndDateCollector(
            fetchers.getRsyncFetcher(config.getRsyncUrl()),
            metrics,
            repositoryObjects);

        return Stream.concat(
            Stream.of(mainRsyncCollector),
            config.getRsyncConfig().getAwsUrl()
                .stream()
                .map(url ->
                    new ObjectAndDateCollector(
                        fetchers.getRsyncFetcher(url),
                        metrics,
                        repositoryObjects)
                ))
            .collect(java.util.stream.Collectors.toList());
    }

    public ObjectAndDateCollector getRrdpCollector() {
        return new ObjectAndDateCollector(fetchers.getRrdpFetcher(config.getRrdpUrl()), metrics, repositoryObjects);
    }

}
