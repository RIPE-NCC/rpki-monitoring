package net.ripe.rpki.monitor.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
@Component
public class CollectorUpdateMetrics {
    public static final String COLLECTOR_UPDATE_DESCRIPTION = "Number of updates by collector by status";
    public static final String COLLECTOR_UPDATE_METRIC = "rpkimonitoring.collector.update";
    public static final String COLLECTOR = "collector";
    public static final String NAME = "name";
    public static final String URL = "url";

    public static final String COLLECTOR_COUNT_DESCRIPTION = "Number of objects by collector by status";
    public static final String COLLECTOR_COUNT_METRIC = "rpkimonitoring.collector.objects";
    public static final String STATUS = "status";

    @Autowired
    private final MeterRegistry registry;

    private final ConcurrentHashMap<Triple<String, String, String>, ExecutionStatus> executionStatus = new ConcurrentHashMap<>();

    public ExecutionStatus trackSuccess(final String collectorName, final String tag, final String url) {
        final var status = getExecutionStatus(collectorName, tag, url);

        status.successCount.increment();
        status.lastUpdated.set(System.currentTimeMillis()/1000);
        return status;
    }

    public ExecutionStatus trackFailure(final String collectorName, final String tag, final String url) {
        final var status = getExecutionStatus(collectorName, tag, url);

        status.failureCount.increment();
        status.lastUpdated.set(System.currentTimeMillis()/1000);
        return status;
    }

    private ExecutionStatus getExecutionStatus(final String collectorName, final String tag, final String repoUrl) {
        return executionStatus.computeIfAbsent(Triple.of(collectorName, tag, repoUrl), key -> new ExecutionStatus(collectorName, tag, repoUrl));
    }

    public class ExecutionStatus {
        private final String collectorName;
        private final String repoTag;
        private final String repoUrl;

        private final AtomicLong lastUpdated = new AtomicLong();

        private final Counter successCount;
        private final Counter failureCount;

        private final AtomicLong passedObjectCount = new AtomicLong();
        private final AtomicLong unknownObjectCount = new AtomicLong();
        private final AtomicLong rejectedObjectCount = new AtomicLong();

        private boolean initialisedCounters = false;

        public ExecutionStatus(String collectorName, String tag, String repoUrl) {
            this.collectorName = collectorName;
            this.repoTag = tag;
            this.repoUrl = repoUrl;

            Gauge.builder("rpkimonitoring.collector.lastupdated", lastUpdated::get)
                    .description("Last update by collector")
                    .tag(COLLECTOR, collectorName)
                    .tag(NAME, tag)
                    .tag(URL, repoUrl)
                    .register(registry);

            successCount = Counter.builder(COLLECTOR_UPDATE_METRIC)
                    .description(COLLECTOR_UPDATE_DESCRIPTION)
                    .tag(COLLECTOR, collectorName)
                    .tag(NAME, tag)
                    .tag(URL, repoUrl)
                    .tag(STATUS, "success")
                    .register(registry);

            failureCount = Counter.builder(COLLECTOR_UPDATE_METRIC)
                    .description(COLLECTOR_UPDATE_DESCRIPTION)
                    .tag(COLLECTOR, collectorName)
                    .tag(NAME, tag)
                    .tag(URL, repoUrl)
                    .tag(STATUS, "failure")
                    .register(registry);
        }

        public ExecutionStatus objectCount(int passed, int rejected, int unknown) {
            // Lazy init metrics so as to never have metrics that always stay at 0.
            if (!initialisedCounters) {
                Gauge.builder(COLLECTOR_COUNT_METRIC, passedObjectCount::get)
                        .description(COLLECTOR_COUNT_DESCRIPTION)
                        .tag(COLLECTOR, collectorName)
                        .tag(NAME, repoTag)
                        .tag(URL, repoUrl)
                        .tag(STATUS, "passed")
                        .register(registry);

                Gauge.builder(COLLECTOR_COUNT_METRIC, rejectedObjectCount::get)
                        .description(COLLECTOR_COUNT_DESCRIPTION)
                        .tag(COLLECTOR, collectorName)
                        .tag(NAME, repoTag)
                        .tag(URL, repoUrl)
                        .tag(STATUS, "rejected")
                        .register(registry);

                Gauge.builder(COLLECTOR_COUNT_METRIC, unknownObjectCount::get)
                        .description(COLLECTOR_COUNT_DESCRIPTION)
                        .tag(COLLECTOR, collectorName)
                        .tag(NAME, repoTag)
                        .tag(URL, repoUrl)
                        .tag(STATUS, "unknown")
                        .register(registry);
                initialisedCounters = true;
            }

            passedObjectCount.set(passed);
            rejectedObjectCount.set(rejected);
            unknownObjectCount.set(unknown);

            return this;
        }

        public void zeroCounters() {
            objectCount(0, 0, 0);
        }
    }
}
