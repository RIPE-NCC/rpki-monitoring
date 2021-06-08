package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RsyncObjectsAboutToExpireCollectorIntegrationTest {

    private ObjectAndDateCollector subject;

    private final RsyncConfig config = new RsyncConfig();
    private RepositoriesState repositories;

    @BeforeEach
    public void beforeEach(@TempDir Path tempDirectory) throws Exception {
        var uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();
        config.setMainUrl(uri.getPath());
        config.setBaseDirectory(tempDirectory);

        var meterRegistry = new SimpleMeterRegistry();
        var rsyncFetcher = new RsyncFetcher(config, config.getMainUrl());
        var collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        repositories = RepositoriesState.init(List.of(Triple.of("rsync", config.getMainUrl(), RepositoryTracker.Type.RSYNC)));
        subject = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, repositories);
    }

    @Test
    public void itShouldUpdateRsyncRepositoryState() throws Exception {
        subject.run();

        var tracker = repositories.getTrackerByTag("rsync").get();
        assertThat(tracker.size(Instant.now())).isEqualTo(5);
    }
}
