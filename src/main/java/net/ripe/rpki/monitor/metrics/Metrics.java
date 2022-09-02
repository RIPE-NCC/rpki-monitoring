package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NoArgsConstructor;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@NoArgsConstructor
public class Metrics {
    public static final String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public static final String PUBLISHED_OBJECT_DIFF = "rpkimonitoring.published.objects.diff";
    public static final String PUBLISHED_OBJECT_COUNT_DESCRIPTION = "Number of published objects";
    public static final String PUBLISHED_OBJECT_PER_TYPE_COUNT_DESCRIPTION = "Number of published objects of each type";
    public static final String PUBLISHED_OBJECT_COUNT = "rpkimonitoring.published.objects.count";
    public static final String PUBLISHED_OBJECT_PER_TYPE_COUNT = "rpkimonitoring.published.per.type.objects.count";

    public static void buildObjectDiffGauge(
            MeterRegistry registry,
            AtomicLong counter,
            PublishedObjectsSummaryService.RepositoryDiffKey diffKey
    ) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", diffKey.lhs().tag())
                .tag("lhs-src", diffKey.lhs().url())
                .tag("rhs", diffKey.rhs().tag())
                .tag("rhs-src", diffKey.rhs().url())
                .tag("threshold", String.valueOf(diffKey.threshold().getSeconds()))
                .tag("type", diffKey.type().name().toLowerCase(Locale.ROOT))
                .register(registry);
    }

    public static void buildObjectCountGauge(MeterRegistry registry, AtomicLong gauge, PublishedObjectsSummaryService.RepositoryKey repo) {
        Gauge.builder(PUBLISHED_OBJECT_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_COUNT_DESCRIPTION)
                .tag("source", repo.tag())
                .tag("url", repo.url())
                .register(registry);
    }

    public static void buildObjectCountGauge(MeterRegistry registry, AtomicLong gauge, PublishedObjectsSummaryService.RepositoryObjectTypeKey key) {
        Gauge.builder(PUBLISHED_OBJECT_PER_TYPE_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_PER_TYPE_COUNT_DESCRIPTION)
                .tag("source", key.key().tag())
                .tag("url", key.key().url())
                .tag("type", key.type().name().toLowerCase(Locale.ROOT))
                .register(registry);
    }
}
