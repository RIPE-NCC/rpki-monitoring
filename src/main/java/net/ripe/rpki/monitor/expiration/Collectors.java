package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import lombok.Getter;
import net.ripe.rpki.monitor.certificateanalysis.CertificateAnalysisService;
import net.ripe.rpki.monitor.certificateanalysis.ObjectConsumer;
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
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Component
public class Collectors {

    private final CollectorUpdateMetrics metrics;
    private final RepositoriesState repositoriesState;

    private final Tracer tracer;

    private final List<ObjectAndDateCollector> rrdpCollectors;
    private final List<ObjectAndDateCollector> rsyncCollectors;

    private final Semaphore threadLimiter;

    private final MeterRegistry registry;

    private final AppConfig config;

    @Autowired
    public Collectors(CollectorUpdateMetrics metrics,
                      RepositoriesState repositoriesState,
                      AppConfig config,
                      FetcherMetrics fetcherMetrics,
                      WebClientBuilderFactory webclientBuilder,
                      CertificateAnalysisService certificateAnalysisService,
                      @Value("${collector.threads}") int numThreads,
                      Optional<Tracer> tracer,
                      MeterRegistry registry) {
        this.config = config;
        this.metrics = metrics;
        this.repositoriesState = repositoriesState;
        this.tracer = tracer.orElse(Tracer.NOOP);
        this.registry = registry;

        threadLimiter = new Semaphore(numThreads);

        // We track only one of the RRDP repositories
        AtomicBoolean primaryRrdp = new AtomicBoolean(false);

        this.rrdpCollectors = config.getRrdpConfig().getTargets().stream().map(
                target -> {
                    if (primaryRrdp.compareAndSet(false, true)) {
                        return makeCollector(new RrdpFetcher(target, fetcherMetrics, webclientBuilder), certificateAnalysisService::process);
                    }
                    return makeCollector(new RrdpFetcher(target, fetcherMetrics, webclientBuilder), o -> {});
                }
        ).toList();
        this.rsyncCollectors = config.getRsyncConfig().getTargets().stream().map(
                target -> makeCollector(new RsyncFetcher(config.getRsyncConfig(), target.name(), target.url(), fetcherMetrics), o -> {})
        ).toList();
    }

    private ObjectAndDateCollector makeCollector(RepoFetcher fetcher, ObjectConsumer objectConsumer) {
        return new ObjectAndDateCollector(fetcher, metrics, repositoriesState, objectConsumer, tracer, config.getProperties().isAcceptAspaV1());
    }
}
