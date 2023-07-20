package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.SneakyThrows;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

public class ObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    protected final List<ObjectAndDateCollector> collectors;

    private final Timer objectCollectorJobTimer;

    private final Semaphore sem;

    public ObjectsAboutToExpireCollectorJob(List<ObjectAndDateCollector> collectors, int numThreads, MeterRegistry registry) {
        this.collectors = collectors;
        this.sem = new Semaphore(numThreads);
        objectCollectorJobTimer = Timer.builder("rpkimonitoring.collector.duration")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofSeconds(2))
                .maximumExpectedValue(Duration.ofMinutes(5))
                .tag("threads", String.valueOf(numThreads))
                .tag("type", this.getClass().getSimpleName())
                .register(registry);
    }

    @SneakyThrows
    private void runCollector(ObjectAndDateCollector collector) {
        collector.run();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            // Executing the parallelstream on a separate pool would cause all derivative work to
            // be spawned on that pool as well.
            //
            // Use a semaphore instead.
            collectors.parallelStream().forEach(collector -> {
                try {
                    sem.acquire();
                    objectCollectorJobTimer.record(() -> this.runCollector(collector));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    sem.release();
                }
            }));
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
