package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

@NoArgsConstructor
public class Metrics {
    public static final String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public static final String PUBLISHED_OBJECT_DIFF = "rpkimonitoring.published.objects.diff";
    public static final String PUBLISHED_OBJECT_COUNT_DESCRIPTION = "Number of published objects";
    public static final String PUBLISHED_OBJECT_COUNT = "rpkimonitoring.published.objects.count";

    public static void buildObjectDiffGauge(MeterRegistry registry, AtomicLong counter, String lhs, String rhs) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", lhs)
                .tag("rhs", rhs)
                .register(registry);
    }

    public static void buildObjectCountGauge(MeterRegistry registry, AtomicLong gauge, String source) {
        Gauge.builder(PUBLISHED_OBJECT_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_COUNT_DESCRIPTION)
                .tag("source", source)
                .register(registry);
    }
}
