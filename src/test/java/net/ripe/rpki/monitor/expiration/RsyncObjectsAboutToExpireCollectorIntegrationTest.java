package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector rsyncObjectsAboutToExpireCollector;

    private RepositoryObjects repositoryObjects = new RepositoryObjects();

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

}