package net.ripe.rpki.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(MockitoExtension.class)
public class PublishedObjectsSummaryTest {
    private final Duration maxThreshold = PublishedObjectsSummaryService.THRESHOLDS.stream().max(Duration::compareTo).get();
    private final Duration minThreshold = PublishedObjectsSummaryService.THRESHOLDS.stream().min(Duration::compareTo).get();

    private final Instant now = Instant.now();
    private RepositoryTracker core, rrdp, rsync;

    private MeterRegistry meterRegistry;
    private PublishedObjectsSummaryService subject;

    @BeforeEach
    public void init() {
        meterRegistry = new SimpleMeterRegistry();
        subject = new PublishedObjectsSummaryService(meterRegistry);

        core = RepositoryTracker.empty("core", "https://ba-apps.ripe.net/certification/", RepositoryTracker.Type.CORE, 3600);
        rrdp = RepositoryTracker.empty("rrdp", "https://rrdp.ripe.net/", RepositoryTracker.Type.RRDP, 3600);
        rsync = RepositoryTracker.empty("rsync", "rsync://rpki.ripe.net/", RepositoryTracker.Type.RSYNC, 3600);
    }

    @Test
    public void itShouldUpdateSizeMetrics() {
        var objects = Stream.of(
                RepositoryEntry.builder()
                    .uri("rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer")
                    .sha256("a9d505c70f1fc166062d1c16f7f200df2d2f89a8377593b5a408daa376de9fe2")
                    .build()
        );
        rrdp.update(now, objects);

        subject.updateSize(now, rrdp);

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isEqualTo(1);
    }

    @Test
    public void itShouldNotReportADifferencesBetweenEmptySources() {
        var res = subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(res.values().stream()).allMatch(Collection::isEmpty);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_core() {
        var objects = Stream.of(
            RepositoryEntry.builder()
                .sha256("not-a-sha256-but-will-do")
                .uri("rsync://example.org/index.txt")
                .build());
        core.update(now.minus(minThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core", "threshold", String.valueOf(minThreshold.getSeconds())).gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold ->
                    then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "core", "threshold", String.valueOf(threshold.getSeconds())).gauges())
                            .allMatch(gauge -> gauge.value() == 0.0)
                );
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rrdp_objects() {
        var objects = Stream.of(
                RepositoryEntry.builder()
                    .sha256("f19e8fbc6d520c06f3424ddf6a53cda830fc8ef4ca7074aa43ad97a42f946d50")
                    .uri("rsync://example.org/file.cer")
                    .build()
        );
        rrdp.update(now.minus(maxThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

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
        var objects = Stream.of(
                RepositoryEntry.builder()
                        .sha256("f19e8fbc6d520c06f3424ddf6a53cda830fc8ef4ca7074aa43ad97a42f946d50")
                        .uri("rsync://example.org/file.cer")
                        .build()
        );
        rsync.update(now.minus(minThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync", "threshold", String.valueOf(minThreshold.getSeconds())).gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold ->
                    then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "rsync", "threshold", String.valueOf(threshold.getSeconds())).gauges())
                            .allMatch(gauge -> gauge.value() == 0.0)
                );

        then(meterRegistry.get(Metrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldDoRsyncDiff() {
        var now = Instant.now();
        var obj1 = RepositoryEntry.builder().uri("url1").sha256("hash1").expiration(Optional.of(now)).build();
        var obj2 = RepositoryEntry.builder().uri("url2").sha256("hash2").expiration(Optional.of(now)).build();

        var beforeThreshold = now.minus(minThreshold);

        core.update(beforeThreshold, Stream.of(obj1, obj2));
        rsync.update(beforeThreshold, Stream.of(obj1));

        var res = subject.getDiff(now, List.of(core), List.of(rsync));
        assertThat(res).hasSize(2);
        var fileEntries1 = res.get(core.getUrl() + "-diff-" + rsync.getUrl() + "-" + minThreshold.getSeconds());
        assertThat(fileEntries1).isNotEmpty();
        var fileEntry = fileEntries1.iterator().next();
        assertThat(fileEntry.getUri()).isEqualTo("url2");
        var fileEntries2 = res.get(rsync.getUrl() + "-diff-" + core.getUrl() + "-" + minThreshold.getSeconds());
        assertThat(fileEntries2).isEmpty();
    }
}
