package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.List;

public class ObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    protected final List<ObjectAndDateCollector> collectors;
    private final PublishedObjectsSummaryService publishedObjectsSummaryService;

    public ObjectsAboutToExpireCollectorJob(
            List<ObjectAndDateCollector> collectors,
            PublishedObjectsSummaryService publishedObjectsSummaryService) {
        this.collectors = collectors;
        this.publishedObjectsSummaryService = publishedObjectsSummaryService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collectors.parallelStream().forEach(collector -> {
                collector.run();
                publishedObjectsSummaryService.processRepositoryUpdate(collector.repositoryUrl());
            });
        } catch (FetcherException e) {
            throw new JobExecutionException(e);
        }
    }
}
