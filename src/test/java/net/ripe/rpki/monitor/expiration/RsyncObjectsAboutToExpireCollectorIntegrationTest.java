package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

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
        config.setMainUrl(uri.getPath());
    }

    @BeforeEach
    public void beforeEach(@TempDir Path tempDirectory) throws IOException {
        meterRegistry = new SimpleMeterRegistry();
        repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

        config.setBaseDirectory(tempDirectory);

        final RsyncFetcher rsyncFetcher = new RsyncFetcher(config, config.getMainUrl());
        final CollectorUpdateMetrics collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        var state = RepositoriesState.init(List.of(Pair.of("rsync", config.getMainUrl())));
        rsyncObjectsAboutToExpireCollector = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, state, repositoryObjects);
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
