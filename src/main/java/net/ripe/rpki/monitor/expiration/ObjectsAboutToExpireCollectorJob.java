package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotException;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.List;

public class ObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    protected final List<ObjectAndDateCollector> collectors;

    public ObjectsAboutToExpireCollectorJob(List<ObjectAndDateCollector> collectors) {
        this.collectors = collectors;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collectors.parallelStream().forEach(ObjectAndDateCollector::run);
        } catch (FetcherException | SnapshotException e) {
            throw new JobExecutionException(e);
        }
    }
}
