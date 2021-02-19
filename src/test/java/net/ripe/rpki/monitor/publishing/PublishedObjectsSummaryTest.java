package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import net.ripe.rpki.monitor.service.core.CoreClient;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(MockitoExtension.class)
public class PublishedObjectsSummaryTest {
    @Mock
    private CoreClient rpkiCoreClient;

    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    private MeterRegistry meterRegistry;

    @BeforeEach
    public void init() {
        meterRegistry = new SimpleMeterRegistry();

        RepositoryObjects repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));
        AppConfig appConfig = new AppConfig();
        publishedObjectsSummaryService = new PublishedObjectsSummaryService(repositoryObjects, rpkiCoreClient, meterRegistry, appConfig);
    }

    @Test
    public void itShouldNotReportADifferencesBetweenEmptySources() {
        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff(Set.of(), Set.of(), Set.of());

        then(res.get("core-diff-rrdp")).isEmpty();
        then(res.get("core-diff-rsync")).isEmpty();
        then(res.get("rsync-diff-core")).isEmpty();
        then(res.get("rsync-diff-rrdp")).isEmpty();
        then(res.get("rrdp-diff-core")).isEmpty();
        then(res.get("rrdp-diff-rsync")).isEmpty();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_core() {
        final Set<PublishedObjectEntry> object = Set.of(
            PublishedObjectEntry.builder()
                .sha256("not-a-sha256-but-will-do")
                .uri("rsync://example.org/index.txt")
                .build());

        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff(object, Set.of(), Set.of());

        then(res.get("core-diff-rrdp")).hasSize(1);
        then(res.get("core-diff-rsync")).hasSize(1);
        then(res.get("rsync-diff-core")).isEmpty();
        then(res.get("rsync-diff-rrdp")).isEmpty();
        then(res.get("rrdp-diff-core")).isEmpty();
        then(res.get("rrdp-diff-rsync")).isEmpty();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rrdp_objects() {
        final var res = publishedObjectsSummaryService
            .getPublishedObjectsDiff(Set.of(), Set.of(RepoObject.fictionalObjectValidAtInstant(new Date())), Set.of());

        then(res.get("core-diff-rrdp")).isEmpty();
        then(res.get("core-diff-rsync")).isEmpty();
        then(res.get("rsync-diff-core")).isEmpty();
        then(res.get("rsync-diff-rrdp")).isEmpty();
        then(res.get("rrdp-diff-core")).hasSize(1);
        then(res.get("rrdp-diff-rsync")).hasSize(1);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                    .tags("source", "rrdp").gauge().value()).isEqualTo(1);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rsync_objects() {
        final var res = publishedObjectsSummaryService
            .getPublishedObjectsDiff(List.of(), Set.of(), Set.of(RepoObject.fictionalObjectValidAtInstant(new Date())));

        then(res.get("core-diff-rrdp")).isEmpty();
        then(res.get("core-diff-rsync")).isEmpty();
        then(res.get("rsync-diff-core")).hasSize(1);
        then(res.get("rsync-diff-rrdp")).hasSize(1);
        then(res.get("rrdp-diff-core")).isEmpty();
        then(res.get("rrdp-diff-rsync")).isEmpty();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }
}
