package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
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
        config.setMainUrl("rsync://rsync.example.org");

        var fetcher = new RsyncFetcher(config, "rsync", "rsync://../../../../../../../");
        then(fetcher.getTargetPath()).startsWith(tempDirectory);
    }

    @Test
    public void itShouldCreateADirectoryPerHost(@TempDir Path tempDirectory) {
        var config = new RsyncConfig();
        config.setBaseDirectory(tempDirectory);
        config.setMainUrl("rsync://rsync.example.org");

        var rsync1Fetcher = new RsyncFetcher(config, "rsync", "rsync://rsync1.example.org");
        var rsync2Fetcher = new RsyncFetcher(config, "rsync","rsync://rsync2.example.org");

        then(rsync1Fetcher.getTargetPath()).isNotEqualByComparingTo(rsync2Fetcher.getTargetPath());
    }
}
