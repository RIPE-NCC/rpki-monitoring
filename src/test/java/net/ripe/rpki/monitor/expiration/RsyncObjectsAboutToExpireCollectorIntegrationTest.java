package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector rsyncObjectsAboutToExpireCollector;

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RepositoryObjects repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

    private final URI uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();

    RsyncObjectsAboutToExpireCollectorIntegrationTest() throws URISyntaxException {
    }

    @BeforeEach
    public void beforeEach() {
        final RsyncFetcher rsyncFetcher = new RsyncFetcher(uri.getPath());

        final var mockMetrics = mock(CollectorUpdateMetrics.class);
        when(mockMetrics.trackSuccess(any(), any())).thenReturn(mock(CollectorUpdateMetrics.ExecutionStatus.class));
        when(mockMetrics.trackFailure(any(), any())).thenReturn(mock(CollectorUpdateMetrics.ExecutionStatus.class));

        rsyncObjectsAboutToExpireCollector = new ObjectAndDateCollector(rsyncFetcher, mockMetrics, repositoryObjects);
    }

    @Test
    public void itShouldPopulateRsyncObjectsSummaryList() throws Exception {
        rsyncObjectsAboutToExpireCollector.run();
        assertEquals(4, repositoryObjects.geRepositoryObjectsAboutToExpire(uri.getPath(), Integer.MAX_VALUE).size());
    }


    @Test
    public void itShouldHaveTimeToExpiryMetricsWhichCountedAllObjects() throws Exception {
        rsyncObjectsAboutToExpireCollector.run();
        final var obj = repositoryObjects.geRepositoryObjectsAboutToExpire(uri.getPath(), Integer.MAX_VALUE);
        rsyncObjectsAboutToExpireCollector.run();
        final var buckets = meterRegistry.get(ObjectExpirationMetrics.COLLECTOR_EXPIRATION_METRIC)
                .tag("url", uri.getPath())
                .summary()
                .takeSnapshot()
                .histogramCounts();

        System.out.println(buckets);
    }
}