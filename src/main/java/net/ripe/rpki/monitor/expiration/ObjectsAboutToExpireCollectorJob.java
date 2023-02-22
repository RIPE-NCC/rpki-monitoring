package net.ripe.rpki.monitor.expiration;

import lombok.SneakyThrows;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotStructureException;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.List;

public class ObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    protected final List<ObjectAndDateCollector> collectors;

    public ObjectsAboutToExpireCollectorJob(List<ObjectAndDateCollector> collectors) {
        this.collectors = collectors;
    }

    private static void safeRunCollector(ObjectAndDateCollector collector) {
        try {
            collector.run();
        } catch (FetcherException | SnapshotStructureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collectors.parallelStream().forEach(ObjectsAboutToExpireCollectorJob::safeRunCollector);
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
