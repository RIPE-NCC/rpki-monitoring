package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PublishedObjectMetrics {
    public static final String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public static final String PUBLISHED_OBJECT_DIFF = "rpkimonitoring.published.objects.diff";
    public static final String PUBLISHED_OBJECT_COUNT_DESCRIPTION = "Number of published objects";
    public static final String PUBLISHED_OBJECT_PER_TYPE_COUNT_DESCRIPTION = "Number of published objects of each type";
    public static final String PUBLISHED_OBJECT_COUNT = "rpkimonitoring.published.objects.count";
    public static final String PUBLISHED_OBJECT_PER_TYPE_COUNT = "rpkimonitoring.published.per.type.objects.count";

    private final Map<PublishedObjectsSummaryService.RepositoryKey, AtomicLong> count = new ConcurrentHashMap<>();
    private final Map<PublishedObjectsSummaryService.RepositoryObjectTypeKey, AtomicLong> countByType = new ConcurrentHashMap<>();
    private final Map<PublishedObjectsSummaryService.RepositoryDiffKey, AtomicLong> countDiff = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    @Autowired
    public PublishedObjectMetrics(@NonNull MeterRegistry registry) {
        this.registry = registry;
    }

    public void trackObjectCount(PublishedObjectsSummaryService.RepositoryKey key, RepositoryTracker.View view) {
        count.computeIfAbsent(key, k -> {
            var count = new AtomicLong(0);

            Gauge.builder(PUBLISHED_OBJECT_COUNT, count::get)
                    .description(PUBLISHED_OBJECT_COUNT_DESCRIPTION)
                    .tag("source", k.tag())
                    .tag("url", k.url())
                    .register(registry);

            return count;
        }).set(view.size());
    }

    public void trackObjectTypeCount(PublishedObjectsSummaryService.RepositoryKey key, RepositoryObjectType type, long size) {
        countByType.computeIfAbsent(new PublishedObjectsSummaryService.RepositoryObjectTypeKey(key, type), tk -> {
            var sizeCount = new AtomicLong(0);

            Gauge.builder(PUBLISHED_OBJECT_PER_TYPE_COUNT, sizeCount::get)
                    .description(PUBLISHED_OBJECT_PER_TYPE_COUNT_DESCRIPTION)
                    .tag("source", tk.tag())
                    .tag("url", tk.url())
                    .tag("type", tk.type().name().toLowerCase(Locale.ROOT))
                    .register(registry);

            return sizeCount;
        }).set(size);
    }

    public void trackDiffSize(PublishedObjectsSummaryService.RepositoryDiffKey diffKey, long size) {
        countDiff.computeIfAbsent(diffKey, dk -> {
            final var diffCount = new AtomicLong(0);
            Gauge.builder(PUBLISHED_OBJECT_DIFF, diffCount::get)
                    .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                    .tag("lhs", diffKey.lhs().tag())
                    .tag("lhs-src", diffKey.lhs().url())
                    .tag("rhs", diffKey.rhs().tag())
                    .tag("rhs-src", diffKey.rhs().url())
                    .tag("threshold", String.valueOf(diffKey.threshold().getSeconds()))
                    .tag("type", diffKey.type().name().toLowerCase(Locale.ROOT))
                    .register(registry);

            return diffCount;
        }).set(size);
    }
}
