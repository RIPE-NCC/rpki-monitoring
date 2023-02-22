package net.ripe.rpki.monitor.expiration;

import lombok.SneakyThrows;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.List;

public class ObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    protected final List<ObjectAndDateCollector> collectors;

    public ObjectsAboutToExpireCollectorJob(List<ObjectAndDateCollector> collectors) {
        this.collectors = collectors;
    }

    @SneakyThrows
    private static void runCollector(ObjectAndDateCollector collector) {
        collector.run();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collectors.parallelStream().forEach(ObjectsAboutToExpireCollectorJob::runCollector);
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
