package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector rsyncObjectsAboutToExpireCollector;

    private MeterRegistry meterRegistry;
    private RepositoryObjects repositoryObjects;

    private final URI uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();

    private final RsyncConfig config;

    RsyncObjectsAboutToExpireCollectorIntegrationTest() throws URISyntaxException {
        config = new RsyncConfig();
        config.setMainUrl("rsync://example.org");
    }

    @BeforeEach
    public void beforeEach(@TempDir Path tempDirectory) throws IOException {
        meterRegistry = new SimpleMeterRegistry();
        repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

        config.setBaseDirectory(tempDirectory);

        final RsyncFetcher rsyncFetcher = new RsyncFetcher(config, uri.getPath());
        final CollectorUpdateMetrics collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        rsyncObjectsAboutToExpireCollector = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, repositoryObjects);
    }

    @Test
    public void itShouldPopulateRsyncObjectsSummaryList() throws Exception {
        rsyncObjectsAboutToExpireCollector.run();
        assertEquals(5, repositoryObjects.geRepositoryObjectsAboutToExpire(uri.getPath(), Integer.MAX_VALUE).size());
    }

    @Test
    public void itShouldSetTheObjectUrlMatchingTheMainUrl() throws Exception {
        rsyncObjectsAboutToExpireCollector.run();

        final var objects = repositoryObjects.getObjects(uri.getPath());
        then(objects).allSatisfy(o -> o.getUri().startsWith(config.getMainUrl()));
    }
}
