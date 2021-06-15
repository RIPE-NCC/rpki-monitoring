package net.ripe.rpki.monitor.publishing;

import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

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
            Triple.of("core", "https://ba-apps.ripe.net/certification", RepositoryTracker.Type.CORE),
            Triple.of("rsync", "rsync://rpki.ripe.net", RepositoryTracker.Type.RSYNC),
            Triple.of("rrdp", "https://rrdp.ripe.net", RepositoryTracker.Type.RRDP)
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
        when(publishedObjectsSummary.getDiff(
                any(Instant.class),
                eq(repositories.trackersOfType(RepositoryTracker.Type.CORE)),
                eq(repositories.trackersOfType(RepositoryTracker.Type.RSYNC))
        )).thenReturn(diff);

        var result = subject.rsyncDiff();
        assertThat(result).isEqualTo(diff);
    }

    @Test
    public void test_get_rrdp_diff() {
        var diff = anyDiff();
        when(publishedObjectsSummary.getDiff(
                any(Instant.class),
                eq(repositories.trackersOfType(RepositoryTracker.Type.CORE)),
                eq(repositories.trackersOfType(RepositoryTracker.Type.RRDP))
        )).thenReturn(diff);

        var result = subject.rrdpDiff();
        assertThat(result).isEqualTo(diff);
    }

    private Map<String, Set<RepositoryEntry>> anyDiff() {
        var entry = RepositoryEntry.builder()
            .uri("rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer")
            .sha256("55c71d1c5d23d18ff782be46c93ed6f76a6c391b60298b6c67e9adae7b3f0d37")
            .build();
        return Map.of("key", Set.of(entry));
    }
}