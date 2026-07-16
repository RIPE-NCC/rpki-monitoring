package net.ripe.rpki.monitor.expiration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import net.ripe.rpki.monitor.config.AppConfig;
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

    private final RsyncConfig rsyncConfig = new RsyncConfig();
    private final AppConfig appConfig = new AppConfig();
    private RepositoriesState repositories;

    @BeforeEach
    public void beforeEach(@TempDir Path tempDirectory) throws Exception {
        var uri = this.getClass().getClassLoader().getResource("rsync_data").toURI();
        rsyncConfig.setRepositoryUrl(uri.getPath());
        rsyncConfig.setDirectories(List.of("ta", "repository"));
        rsyncConfig.setBaseDirectory(tempDirectory);
        appConfig.setRsyncConfig(rsyncConfig);

        var meterRegistry = new SimpleMeterRegistry();
        var rsyncFetcher = new RsyncFetcher(rsyncConfig, "rsync", rsyncConfig.getRepositoryUrl(), new FetcherMetrics(new SimpleMeterRegistry()));
        var collectorUpdateMetrics = new CollectorUpdateMetrics(meterRegistry);

        repositories = RepositoriesState.init(List.of(Triple.of("rsync", rsyncConfig.getRepositoryUrl(), RepositoryTracker.Type.RSYNC)), Duration.ZERO);
        subject = new ObjectAndDateCollector(rsyncFetcher, collectorUpdateMetrics, repositories, objects -> {}, Tracer.NOOP, appConfig);
    }

    @Test
    public void itShouldUpdateRsyncRepositoryState() throws Exception {
        subject.run();

        var tracker = repositories.getTrackerByTag("rsync").get();
        assertThat(tracker.view(Instant.now()).size()).isEqualTo(5);
    }
}
