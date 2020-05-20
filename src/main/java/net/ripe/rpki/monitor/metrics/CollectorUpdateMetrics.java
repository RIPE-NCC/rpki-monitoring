package net.ripe.rpki.monitor.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
@Component
public class CollectorUpdateMetrics {
    public final static String COLLECTOR_UPDATE_DESCRIPTION = "Number of updates by collector by status";
    public static final String COLLECTOR_UPDATE_METRIC = "rpkimonitoring.collector.update";
    public static final String COLLECTOR = "collector";

    @Autowired
    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, ExecutionStatus> executionStatus = new ConcurrentHashMap<>();

    public void trackSuccess(final String collectorName) {
        final var status = getExecutionStatus(collectorName);

        status.successCount.increment();
        status.lastUpdated.set(System.currentTimeMillis()/1000);
    }

    public void trackFailure(final String collectorName) {
        final var status = getExecutionStatus(collectorName);

        status.failureCount.increment();
    }

    private ExecutionStatus getExecutionStatus(final String collectorName) {
        return executionStatus.computeIfAbsent(collectorName, ExecutionStatus::new);
    }

    private class ExecutionStatus {
        private final AtomicLong lastUpdated = new AtomicLong();

        private final Counter successCount;
        private final Counter failureCount;

        public ExecutionStatus(String collectorName) {
            Gauge.builder("rpkimonitoring.collector.lastupdated", lastUpdated::get)
                    .description("Last update by collector")
                    .tag(COLLECTOR, collectorName)
                    .register(registry);

            successCount = Counter.builder(COLLECTOR_UPDATE_METRIC)
                    .description(COLLECTOR_UPDATE_DESCRIPTION)
                    .tag(COLLECTOR, collectorName)
                    .tag("status", "success")
                    .register(registry);

            failureCount = Counter.builder(COLLECTOR_UPDATE_METRIC)
                    .description(COLLECTOR_UPDATE_DESCRIPTION)
                    .tag(COLLECTOR, collectorName)
                    .tag("status", "failure")
                    .register(registry);
        }
    }
}
