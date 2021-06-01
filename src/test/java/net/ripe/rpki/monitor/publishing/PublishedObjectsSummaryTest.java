package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.RrdpConfig;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.*;

@ExtendWith(MockitoExtension.class)
public class PublishedObjectsSummaryTest {
    private final Duration maxThreshold = PublishedObjectsSummaryService.THRESHOLDS.stream().max(Duration::compareTo).get();
    private final Duration minThreshold = PublishedObjectsSummaryService.THRESHOLDS.stream().min(Duration::compareTo).get();

    private final Instant now = Instant.now();
    private final AppConfig testConfig = mkTestConfig();

    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    @Mock
    private CoreClient rpkiCoreClient;

    private MeterRegistry meterRegistry;

    @BeforeEach
    public void init() {
        meterRegistry = new SimpleMeterRegistry();
        var repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));
        publishedObjectsSummaryService = createSummaryService(testConfig, repositoryObjects);
    }

    private PublishedObjectsSummaryService createSummaryService(AppConfig appConfig, RepositoryObjects repositoryObjects) {
        return new PublishedObjectsSummaryService(repositoryObjects, rpkiCoreClient, meterRegistry, appConfig);
    }

    @Test
    public void itShouldNotReportADifferencesBetweenEmptySources() {
        final var res = publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(
                now,
                List.of(
                    RepositoryTracker.empty("core", testConfig.getCoreUrl()),
                    RepositoryTracker.empty("rrdp", testConfig.getRrdpConfig().getMainUrl()),
                    RepositoryTracker.empty("rsync", testConfig.getRsyncConfig().getMainUrl())
                )
        );

        then(res.values().stream()).allMatch(Collection::isEmpty);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void processRepositoryUpdateShouldInitializeCounterForTracker(){

        publishedObjectsSummaryService.processRepositoryUpdate("https://rrdp.rpki.ripe.net");
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge()).isNotNull();

        publishedObjectsSummaryService.processRepositoryUpdate("rsync://rpki.ripe.net");
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge()).isNotNull();

        publishedObjectsSummaryService.processRepositoryUpdate("https://ba-apps.ripe.net/certification/");
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge()).isNotNull();

    }

    @Test
    public void itShouldReportADifference_caused_by_core() {
        final Set<PublishedObjectEntry> object = Set.of(
            PublishedObjectEntry.builder()
                .sha256("not-a-sha256-but-will-do")
                .uri("rsync://example.org/index.txt")
                .build());

        publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(
                now,
                List.of(
                    RepositoryTracker.with("core", testConfig.getCoreUrl(), now.minus(minThreshold), object),
                    RepositoryTracker.empty("rrdp", testConfig.getRrdpConfig().getMainUrl()),
                    RepositoryTracker.empty("rsync", testConfig.getRsyncConfig().getMainUrl())
                )
        );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core", "threshold", String.valueOf(minThreshold.getSeconds())).gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold -> {
                    then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "core", "threshold", String.valueOf(threshold.getSeconds())).gauges())
                            .allMatch(gauge -> gauge.value() == 0.0);
                });
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rrdp_objects() {
        Set<RepoObject> object = Set.of(RepoObject.fictionalObjectValidAtInstant(new Date()));

        publishedObjectsSummaryService
            .updateAndGetPublishedObjectsDiff(
                    now,
                    List.of(
                        RepositoryTracker.empty("core", testConfig.getCoreUrl()),
                        RepositoryTracker.with("rrdp", testConfig.getRrdpConfig().getMainUrl(), now.minus(maxThreshold), object),
                        RepositoryTracker.empty("rsync", testConfig.getRsyncConfig().getMainUrl())
                    )
            );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                    .tags("source", "rrdp").gauge().value()).isEqualTo(1);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rsync_objects() {
        Set<RepoObject> object = Set.of(RepoObject.fictionalObjectValidAtInstant(new Date()));

        publishedObjectsSummaryService.updateAndGetPublishedObjectsDiff(
                now,
                List.of(
                    RepositoryTracker.empty("core", testConfig.getCoreUrl()),
                    RepositoryTracker.empty("rrdp", testConfig.getRrdpConfig().getMainUrl()),
                    RepositoryTracker.with("rsync", testConfig.getRsyncConfig().getMainUrl(), now.minus(minThreshold), object)
                )
        );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "core").gauge().value()).isZero();
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rsync").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isZero();

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync", "threshold", String.valueOf(minThreshold.getSeconds())).gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold -> {
                    then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "rsync", "threshold", String.valueOf(threshold.getSeconds())).gauges())
                            .allMatch(gauge -> gauge.value() == 0.0);
                });

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldDoRsyncDiff() {
        final String mainUrl = "https://rsync.ripe.net";
        testConfig.getRsyncConfig().setMainUrl(mainUrl);
        final String secondaryUrl = "https://rsync-secondary.ripe.net";
        testConfig.getRsyncConfig().setOtherUrls(Maps.newHashMap("secondary", secondaryUrl));

        final RepositoryObjects repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

        final Date date = new Date();
        final RepoObject obj1 = new RepoObject(date, date, "url1", new byte[]{1, 2, 3, 3});
        final RepoObject obj2 = new RepoObject(date, date, "url2", new byte[]{1, 4, 5, 6});

        repositoryObjects.setRepositoryObject(mainUrl, new TreeSet<>(Set.of(obj1, obj2)));
        repositoryObjects.setRepositoryObject(secondaryUrl, new TreeSet<>(Set.of(obj1)));

        PublishedObjectsSummaryService publishedObjectsSummaryService = createSummaryService(testConfig, repositoryObjects);

        final var res = publishedObjectsSummaryService.getRsyncDiff(Instant.now(), Duration.ofSeconds(0));
        assertNotNull(res);
        assertEquals(2, res.size());
        final Set<FileEntry> fileEntries1 = res.get(mainUrl + "-diff-" + secondaryUrl + "-0");
        final FileEntry fileEntry = fileEntries1.iterator().next();
        assertFalse(fileEntries1.isEmpty());
        assertEquals("url2", fileEntry.getUri());

        final Set<FileEntry> fileEntries2 = res.get(secondaryUrl + "-diff-" + mainUrl + "-0");
        assertTrue(fileEntries2.isEmpty());
    }

    @Test
    public void itShouldDoRrdpDiff() {
        final String mainUrl = "https://rrdp.ripe.net";
        testConfig.getRrdpConfig().setMainUrl(mainUrl);
        final String secondaryUrl = "https://rrdp-secondary.ripe.net";
        testConfig.getRrdpConfig().setOtherUrls(Maps.newHashMap("secondary", secondaryUrl));

        final RepositoryObjects repositoryObjects = new RepositoryObjects(new ObjectExpirationMetrics(meterRegistry));

        final Date date = new Date();
        final RepoObject obj1 = new RepoObject(date, date, "url1", new byte[]{1, 2, 3, 3});
        final RepoObject obj2 = new RepoObject(date, date, "url2", new byte[]{1, 4, 5, 6});

        repositoryObjects.setRepositoryObject(mainUrl, new TreeSet<>(Set.of(obj1, obj2)));
        repositoryObjects.setRepositoryObject(secondaryUrl, new TreeSet<>(Set.of(obj1)));

        PublishedObjectsSummaryService publishedObjectsSummaryService = createSummaryService(testConfig, repositoryObjects);

        final var res = publishedObjectsSummaryService.getRrdpDiff(Instant.now(), Duration.ofSeconds(0));
        assertNotNull(res);
        assertEquals(2, res.size());
        final Set<FileEntry> fileEntries1 = res.get(mainUrl + "-diff-" + secondaryUrl + "-0");
        assertFalse(fileEntries1.isEmpty());
        final FileEntry fileEntry = fileEntries1.iterator().next();
        assertEquals("url2", fileEntry.getUri());

        final Set<FileEntry> fileEntries2 = res.get(secondaryUrl + "-diff-" + mainUrl + "-0");
        assertTrue(fileEntries2.isEmpty());
    }

    private static AppConfig mkTestConfig() {
        var rrdp = new RrdpConfig();
        rrdp.setMainUrl("https://rrdp.rpki.ripe.net");
        var rsync = new RsyncConfig();
        rsync.setMainUrl("rsync://rpki.ripe.net");
        var config = new AppConfig();
        config.setRrdpConfig(rrdp);
        config.setRsyncConfig(rsync);
        config.setCoreUrl("https://ba-apps.ripe.net/certification/");
        return config;
    }

}
