package net.ripe.rpki.monitor.publishing;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.monitor.metrics.PublishedObjectMetrics;
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
        subject = new PublishedObjectsSummaryService(new PublishedObjectMetrics(meterRegistry));

        core = RepositoryTracker.empty("core", "https://ba-apps.ripe.net/certification/", RepositoryTracker.Type.CORE, Duration.ofSeconds(3600));
        rrdp = RepositoryTracker.empty("rrdp", "https://rrdp.ripe.net/", RepositoryTracker.Type.RRDP, Duration.ofSeconds(3600));
        rsync = RepositoryTracker.empty("rsync", "rsync://rpki.ripe.net/", RepositoryTracker.Type.RSYNC, Duration.ofSeconds(3600));
    }

    @Test
    public void itShouldUpdateSizeMetrics() {
        var objects = Stream.of(
                RepositoryEntry.builder()
                    .uri("rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer")
                    .sha256(HashCode.fromString("a9d505c70f1fc166062d1c16f7f200df2d2f89a8377593b5a408daa376de9fe2").asBytes())
                    .build(),
            RepositoryEntry.builder()
                .uri("rsync://rpki.ripe.net/repository/DEFAULT/123.roa")
                .sha256(HashCode.fromString("a9d625c70f1fc166062d1c16f7f200df2d2f19a8377593b5a408daa376de9fe2").asBytes())
                .build(),
            RepositoryEntry.builder()
                .uri("rsync://rpki.ripe.net/repository/DEFAULT/abc.mft")
                .sha256(HashCode.fromString("6af51abb58e3d7d01f9e32ef62dbcb8ac13d91ef31194e20aabae796cf40c0d3").asBytes())
                .build()
        );
        rrdp.update(now, objects);

        subject.updateSizes(now, rrdp);

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_COUNT)
                .tags("source", "rrdp").gauge().value()).isEqualTo(3);

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_PER_TYPE_COUNT)
                .tags("source", "rrdp", "type", "certificate").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_PER_TYPE_COUNT)
                .tags("source", "rrdp", "type", "roa").gauge().value()).isEqualTo(1);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_PER_TYPE_COUNT)
                .tags("source", "rrdp", "type", "manifest").gauge().value()).isEqualTo(1);
    }

    @Test
    public void itShouldNotReportADifferencesBetweenEmptySources() {
        var res = subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(res.values().stream()).allMatch(Collection::isEmpty);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF).gauges())
            .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_core() {
        var objects = Stream.of(
            RepositoryEntry.builder()
                .sha256(Hashing.sha256().hashUnencodedChars("not-a-sha256-but-will-do").asBytes())
                .uri("rsync://example.org/index.txt")
                .build());
        core.update(now.minus(minThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core", "threshold", String.valueOf(minThreshold.getSeconds()), "type", "unknown").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold ->
                    then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "core", "threshold", String.valueOf(threshold.getSeconds()), "type", "certificate").gauges())
                            .allMatch(gauge -> gauge.value() == 0.0)
                );
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rrdp_objects() {
        var objects = Stream.of(
                RepositoryEntry.builder()
                    .sha256(HashCode.fromString("f19e8fbc6d520c06f3424ddf6a53cda830fc8ef4ca7074aa43ad97a42f946d50").asBytes())
                    .uri("rsync://example.org/file.cer")
                    .build()
        );
        rrdp.update(now.minus(maxThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp", "type", "certificate").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
    }

    @Test
    public void itShouldReportADifference_caused_by_rsync_objects() {
        var objects = Stream.of(
                RepositoryEntry.builder()
                        .sha256(HashCode.fromString("f19e8fbc6d520c06f3424ddf6a53cda830fc8ef4ca7074aa43ad97a42f946d50").asBytes())
                        .uri("rsync://example.org/file.cer")
                        .build()
        );
        rsync.update(now.minus(minThreshold), objects);

        subject.updateAndGetPublishedObjectsDiff(
                now,
                List.of(core, rrdp, rsync)
        );

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "core").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rsync", "threshold", String.valueOf(minThreshold.getSeconds()), "type", "certificate").gauges())
                .allMatch(gauge -> gauge.value() == 1.0);
        PublishedObjectsSummaryService.THRESHOLDS.stream()
                .filter(x -> x.compareTo(minThreshold) > 0)
                .forEach(threshold ->
                    then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                            .tags("lhs", "rsync", "threshold", String.valueOf(threshold.getSeconds()), "type", "certificate").gauges())
                            .allMatch(gauge -> gauge.value() == 0.0)
                );

        then(meterRegistry.get(PublishedObjectMetrics.PUBLISHED_OBJECT_DIFF)
                .tags("lhs", "rrdp").gauges())
                .allMatch(gauge -> gauge.value() == 0.0);
    }

    @Test
    public void itShouldDoRsyncDiff() {
        var now = Instant.now();
        var obj1 = RepositoryEntry.builder().uri("url1.cer").sha256(Hashing.sha256().hashUnencodedChars("hash1").asBytes()).expiration(Optional.of(now)).build();
        var obj2 = RepositoryEntry.builder().uri("url2.roa").sha256(Hashing.sha256().hashUnencodedChars("hash2").asBytes()).expiration(Optional.of(now)).build();

        var beforeThreshold = now.minus(minThreshold);

        core.update(beforeThreshold, Stream.of(obj1, obj2));
        rsync.update(beforeThreshold, Stream.of(obj1));

        var res = subject.getDiff(now, List.of(core), List.of(rsync));
        assertThat(res).hasSize(2 * RepositoryObjectType.values().length);
        var fileEntries1 = res.get(new PublishedObjectsSummaryService.RepositoryDiffKey(core.key(), rsync.key(), RepositoryObjectType.Roa, minThreshold));
        assertThat(fileEntries1).isNotEmpty();
        var fileEntry = fileEntries1.iterator().next();
        assertThat(fileEntry.getUri()).isEqualTo("url2.roa");

        assertThat(res.get(new PublishedObjectsSummaryService.RepositoryDiffKey(rsync.key(), core.key(), RepositoryObjectType.Roa, minThreshold))).isEmpty();
        assertThat(res.get(new PublishedObjectsSummaryService.RepositoryDiffKey(rsync.key(), core.key(), RepositoryObjectType.Certificate, minThreshold))).isEmpty();
    }
}
