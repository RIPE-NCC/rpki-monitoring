package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class RsyncObjectsAboutToExpireCollector extends AbstractObjectsAboutToExpireCollector {

    private final SummaryService summaryService;

    @Autowired
    public RsyncObjectsAboutToExpireCollector(
            final SummaryService summaryService,
            @Qualifier("RsyncFetcher") final RepoFetcher repoFetcher,
            final MeterRegistry registry) {
        super(repoFetcher, registry);
        this.summaryService = summaryService;
    }

    @Override
    protected void setSummary(ConcurrentSkipListSet<RepoObject> expirationSummary) {
        summaryService.setRsyncSummary(expirationSummary);
    }
}
