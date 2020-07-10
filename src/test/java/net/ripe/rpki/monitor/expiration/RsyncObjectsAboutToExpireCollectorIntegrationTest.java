package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private SummaryService summaryService;

    private RsyncObjectsAboutToExpireCollector rsyncObjectsAboutToExpireCollector;


    @BeforeEach
    public void beforeEach() throws Exception {
        final URI uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();

        final RsyncFetcher rsyncFetcher = new RsyncFetcher();
        rsyncFetcher.setRsyncUrl(uri.getPath());
        summaryService = new SummaryService();

        rsyncObjectsAboutToExpireCollector = new RsyncObjectsAboutToExpireCollector(summaryService, rsyncFetcher, mock(CollectorUpdateMetrics.class));

    }

    @Test
    public void itShouldPopulateRsyncObjectsSummaryList() throws Exception {

        rsyncObjectsAboutToExpireCollector.run();

        assertEquals(4,summaryService.getRsyncObjectsAboutToExpire(Integer.MAX_VALUE).size());
    }

}