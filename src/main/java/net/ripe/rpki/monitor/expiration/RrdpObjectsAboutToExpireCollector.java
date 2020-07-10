package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class RrdpObjectsAboutToExpireCollector extends AbstractObjectsAboutToExpireCollector {

    private final SummaryService summaryService;

    @Autowired
    public RrdpObjectsAboutToExpireCollector(
            final SummaryService summaryService,
            @Qualifier("RrdpFetcher") final RepoFetcher repoFetcher,
            final CollectorUpdateMetrics collectorUpdateMetrics) {
        super(repoFetcher, collectorUpdateMetrics);
        this.summaryService = summaryService;
    }

    @Override
    protected void setSummary(ConcurrentSkipListSet<RepoObject> expirationSummary) {
        summaryService.setRrdpSummary(expirationSummary);
    }
}
