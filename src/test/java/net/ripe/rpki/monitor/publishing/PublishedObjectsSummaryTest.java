package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.expiration.SummaryService;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class PublishedObjectsSummaryTest {
    @Mock
    private SummaryService summaryService;

    @Mock
    private CoreClient rpkiCoreClient;

    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    private MeterRegistry meterRegistry;

    @BeforeEach
    public void init() {
        meterRegistry = new SimpleMeterRegistry();

        publishedObjectsSummaryService = new PublishedObjectsSummaryService(summaryService, rpkiCoreClient, meterRegistry, mock(CollectorUpdateMetrics.class));
    }

    @Test
    public void itShouldNotReportADifferencesBetweenEmptySources() {
        given(summaryService.getRrdpObjects()).willReturn(Set.of());
        given(summaryService.getRsynObjects()).willReturn(Set.of());
        given(rpkiCoreClient.publishedObjects()).willReturn(List.of());

        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff();

        then(res.getInCoreNotInRRDP()).isEmpty();
        then(res.getInCoreNotInRsync()).isEmpty();
        then(res.getInRsyncNotInCore()).isEmpty();
        then(res.getInRsyncNotInRRDP()).isEmpty();
        then(res.getInRRDPNotInCore()).isEmpty();
        then(res.getInRRDPNotInRsync()).isEmpty();

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_core() {
        given(summaryService.getRrdpObjects()).willReturn(Set.of());
        given(summaryService.getRsynObjects()).willReturn(Set.of());
        given(rpkiCoreClient.publishedObjects()).willReturn(List.of(
                PublishedObjectEntry.builder()
                        .sha256("not-a-sha256-but-will-do")
                        .uri("rsync://example.org/index.txt")
                        .build()));

        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff();

        then(res.getInCoreNotInRRDP()).hasSize(1);
        then(res.getInCoreNotInRsync()).hasSize(1);
        then(res.getInRsyncNotInCore()).isEmpty();
        then(res.getInRsyncNotInRRDP()).isEmpty();
        then(res.getInRRDPNotInCore()).isEmpty();
        then(res.getInRRDPNotInRsync()).isEmpty();

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rrdp_objects() {
        given(summaryService.getRrdpObjects()).willReturn(Set.of(RepoObject.fictionalObjectExpiringOn(new Date())));
        given(summaryService.getRsynObjects()).willReturn(Set.of());
        given(rpkiCoreClient.publishedObjects()).willReturn(List.of());

        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff();

        then(res.getInCoreNotInRRDP()).isEmpty();
        then(res.getInCoreNotInRsync()).isEmpty();
        then(res.getInRsyncNotInCore()).isEmpty();
        then(res.getInRsyncNotInRRDP()).isEmpty();
        then(res.getInRRDPNotInCore()).hasSize(1);
        then(res.getInRRDPNotInRsync()).hasSize(1);

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                    .tags("source", "rrdp").gauge().value()).isEqualTo(1);

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rsync_objects() {
        given(summaryService.getRrdpObjects()).willReturn(Set.of());
        given(summaryService.getRsynObjects()).willReturn(Set.of(RepoObject.fictionalObjectExpiringOn(new Date())));
        given(rpkiCoreClient.publishedObjects()).willReturn(List.of());

        final var res = publishedObjectsSummaryService.getPublishedObjectsDiff();

        then(res.getInCoreNotInRRDP()).isEmpty();
        then(res.getInCoreNotInRsync()).isEmpty();
        then(res.getInRsyncNotInCore()).hasSize(1);
        then(res.getInRsyncNotInRRDP()).hasSize(1);
        then(res.getInRRDPNotInCore()).isEmpty();
        then(res.getInRRDPNotInRsync()).isEmpty();

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        then(meterRegistry.get(PublishedObjectsSummaryService.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }
}
