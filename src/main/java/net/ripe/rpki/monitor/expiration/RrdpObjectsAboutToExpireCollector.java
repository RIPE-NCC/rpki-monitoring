package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class RrdpObjectsAboutToExpireCollector extends AbstractObjectsAboutToExpireCollector {

    private final SummaryService summaryService;

    @Autowired
    public RrdpObjectsAboutToExpireCollector(final SummaryService summaryService, @Qualifier("RrdpFetcher") final RepoFetcher repoFetcher) {
        super(repoFetcher);
        this.summaryService = summaryService;
    }

    @Override
    protected void setSummary(ConcurrentSkipListSet<RepoObject> expirationSummary) {
        summaryService.setRrdpSummary(expirationSummary);
    }
}
