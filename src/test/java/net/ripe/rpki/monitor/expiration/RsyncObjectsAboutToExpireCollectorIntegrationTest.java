package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import net.ripe.rpki.monitor.config.RsyncConfig;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
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
        config.setRepositoryUrl(uri.getPath());
        config.setDirectories(List.of("ta", "repository"));
        config.setBaseDirectory(tempDirectory);

        var meterRegistry = new SimpleMeterRegistry();
        var rsyncFetcher = new RsyncFetcher(config, "rsync", config.getRepositoryUrl(), new FetcherMetrics(new SimpleMeterRegistry()));
        var collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        repositories = RepositoriesState.init(List.of(Triple.of("rsync", config.getRepositoryUrl(), RepositoryTracker.Type.RSYNC)), Duration.ZERO);
        subject = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, repositories, objects -> {}, Tracer.NOOP);
    }

    @Test
    public void itShouldUpdateRsyncRepositoryState() throws Exception {
        subject.run();

        var tracker = repositories.getTrackerByTag("rsync").get();
        assertThat(tracker.view(Instant.now()).size()).isEqualTo(5);
    }
}
