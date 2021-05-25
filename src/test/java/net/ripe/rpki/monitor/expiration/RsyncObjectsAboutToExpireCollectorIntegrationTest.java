package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector rsyncObjectsAboutToExpireCollector;

    private MeterRegistry meterRegistry;
    private RepositoryObjects repositoryObjects;

    private final URI uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();

    private final RsyncConfig config;

    private Path tempDirectory;

    RsyncObjectsAboutToExpireCollectorIntegrationTest() throws URISyntaxException {
        config = new RsyncConfig();
        config.setMainUrl("rsync://example.org");
    }

    @BeforeEach
    public void beforeEach() throws IOException {
        meterRegistry = new SimpleMeterRegistry();
        repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

        tempDirectory = Files.createTempDirectory("rsync-objects-about-to-expire-test");
        config.setBaseDirectory(tempDirectory);

        final RsyncFetcher rsyncFetcher = new RsyncFetcher(config, uri.getPath());
        final CollectorUpdateMetrics collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        rsyncObjectsAboutToExpireCollector = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, repositoryObjects);
    }

    @AfterEach
    public void afterEach() throws  IOException {
        try {
            Files.walk(tempDirectory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            log.error("Failed to cleanup", e);
        }
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
