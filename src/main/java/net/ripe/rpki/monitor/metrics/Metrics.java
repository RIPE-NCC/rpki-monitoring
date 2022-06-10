package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@NoArgsConstructor
public class Metrics {
    public static final String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public static final String PUBLISHED_OBJECT_DIFF = "rpkimonitoring.published.objects.diff";
    public static final String PUBLISHED_OBJECT_COUNT_DESCRIPTION = "Number of published objects";
    public static final String PUBLISHED_OBJECT_COUNT = "rpkimonitoring.published.objects.count";

    public static void buildObjectDiffGauge(
            MeterRegistry registry,
            AtomicLong counter,
            String lhs,
            String lhsSource,
            String rhs,
            String rhsSource,
            Duration threshold
    ) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", lhs)
                .tag("lhs-src", lhsSource)
                .tag("rhs", rhs)
                .tag("rhs-src", rhsSource)
                .tag("threshold", String.valueOf(threshold.getSeconds()))
                .register(registry);
    }

    public static void buildObjectDiffGauge(
            MeterRegistry registry,
            AtomicLong counter,
            String lhs,
            String lhsSource,
            String rhs,
            String rhsSource,
            Duration threshold,
            String objectType
    ) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", lhs)
                .tag("lhs-src", lhsSource)
                .tag("rhs", rhs)
                .tag("rhs-src", rhsSource)
                .tag("threshold", String.valueOf(threshold.getSeconds()))
                .tag("type", objectType)
                .register(registry);
    }

    public static void buildObjectCountGauge(MeterRegistry registry, AtomicLong gauge, String source) {
        Gauge.builder(PUBLISHED_OBJECT_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_COUNT_DESCRIPTION)
                .tag("source", source)
                .register(registry);
    }

    public static void buildObjectCountGauge(MeterRegistry registry, AtomicLong gauge, String source, String objectType) {
        Gauge.builder(PUBLISHED_OBJECT_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_COUNT_DESCRIPTION)
                .tag("source", source)
                .tag("type", objectType)
                .register(registry);
    }
}
