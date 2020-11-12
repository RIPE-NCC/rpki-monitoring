package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector rsyncObjectsAboutToExpireCollector;

    private RepositoryObjects repositoryObjects = new RepositoryObjects();

    private final URI uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();

    RsyncObjectsAboutToExpireCollectorIntegrationTest() throws URISyntaxException {
    }

    @BeforeEach
    public void beforeEach() {
        final RsyncFetcher rsyncFetcher = new RsyncFetcher(uri.getPath());

        rsyncObjectsAboutToExpireCollector = new ObjectAndDateCollector(rsyncFetcher, mock(CollectorUpdateMetrics.class), repositoryObjects);
    }

    @Test
    public void itShouldPopulateRsyncObjectsSummaryList() throws Exception {
        rsyncObjectsAboutToExpireCollector.run();
        assertEquals(4, repositoryObjects.geRepositoryObjectsAboutToExpire(uri.getPath(), Integer.MAX_VALUE).size());
    }

}