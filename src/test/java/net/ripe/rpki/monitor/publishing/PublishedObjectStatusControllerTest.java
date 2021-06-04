package net.ripe.rpki.monitor.publishing;

import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishedObjectStatusControllerTest {
    private final PublishedObjectsSummaryService publishedObjectsSummary = mock(PublishedObjectsSummaryService.class);
    private final RepositoriesState repositories = RepositoriesState.init(List.of(
            Pair.of("rsync", "rsync://rpki.ripe.net")
    ));
    private final PublishedObjectStatusController subject = new PublishedObjectStatusController(publishedObjectsSummary, repositories);

    @Test
    public void test_get_published_object_diff() {
        var diff = anyDiff();
        when(publishedObjectsSummary.updateAndGetPublishedObjectsDiff(
                any(Instant.class),
                eq(repositories.allTrackers())
        )).thenReturn(diff);

        var result = subject.publishedObjectDiffs();
        assertThat(result).isEqualTo(diff);
    }

    @Test
    public void test_get_rsync_diff() {
        var diff = anyDiff();
        when(publishedObjectsSummary.getRsyncDiff(
                any(Instant.class),
                eq(Duration.ofSeconds(300))
        )).thenReturn(diff);

        var result = subject.rsyncDiff();
        assertThat(result).isEqualTo(diff);
    }

    @Test
    public void test_get_rrdp_diff() {
        var diff = anyDiff();
        when(publishedObjectsSummary.getRrdpDiff(
                any(Instant.class),
                eq(Duration.ofSeconds(300))
        )).thenReturn(diff);

        var result = subject.rrdpDiff();
        assertThat(result).isEqualTo(diff);
    }

    private Map<String, Set<FileEntry>> anyDiff() {
        var entry = new FileEntry(
                "rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer",
                "55c71d1c5d23d18ff782be46c93ed6f76a6c391b60298b6c67e9adae7b3f0d37"
        );
        return Map.of("key", Set.of(entry));
    }
}