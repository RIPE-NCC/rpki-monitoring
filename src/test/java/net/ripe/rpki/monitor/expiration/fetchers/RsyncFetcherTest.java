package net.ripe.rpki.monitor.expiration.fetchers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;

@Slf4j
public class RsyncFetcherTest {
    @Test
    public void itShouldNotBeVulnerableToPathTraversalInHost(@TempDir Path tempDirectory) {
        var config = new RsyncConfig();
        config.setBaseDirectory(tempDirectory);
        config.setRepositoryUrl("rsync://rsync.example.org");

        var fetcher = new RsyncFetcher(config, "rsync", "rsync://../../../../../../../", new FetcherMetrics(new SimpleMeterRegistry()));
        then(fetcher.getTargetPath()).startsWith(tempDirectory);
    }

    @Test
    public void itShouldCreateADirectoryPerHost(@TempDir Path tempDirectory) {
        var config = new RsyncConfig();
        config.setBaseDirectory(tempDirectory);
        config.setRepositoryUrl("rsync://rsync.example.org");

        var rsync1Fetcher = new RsyncFetcher(config, "rsync", "rsync://rsync1.example.org", new FetcherMetrics(new SimpleMeterRegistry()));
        var rsync2Fetcher = new RsyncFetcher(config, "rsync","rsync://rsync2.example.org", new FetcherMetrics(new SimpleMeterRegistry()));

        then(rsync1Fetcher.getTargetPath()).isNotEqualByComparingTo(rsync2Fetcher.getTargetPath());
    }
}
