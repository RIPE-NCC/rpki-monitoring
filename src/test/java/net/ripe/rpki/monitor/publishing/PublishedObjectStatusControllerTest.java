package net.ripe.rpki.monitor.publishing;

import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
    ), Duration.ZERO);
    private final RepositoryEntry object = new RepositoryEntry(
            "rsync://example.com/repository/DEFAULT/xyz.cer",
            "02c2a8e60fde7d630eb3b44c61b1b0326d6f664c9d3d4be6f5cef4393f8bd468",
            Optional.of(Instant.now()),
            Optional.empty()
    );
    private final Instant now = Instant.now();

    private final PublishedObjectStatusController subject = new PublishedObjectStatusController(publishedObjectsSummary, repositories);

    // Tests mostly validate that value is directly returned.
    @SuppressWarnings("unchecked")
    @Test
    public void test_get_published_object_diff() {
        var diff = (Map<PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>>)mock(Map.class);
        when(publishedObjectsSummary.updateAndGetPublishedObjectsDiff(
                any(Instant.class),
                eq(repositories.allTrackers())
        )).thenReturn(diff);

        var result = subject.publishedObjectDiffs();
        assertThat(result).isEqualTo(diff);
    }

    @Test
    public void test_get_repository_diff() {
        repositories.updateByTag("core", now, Stream.of(object));
        assertThat(subject.diff("core", "rsync", 0).size()).isOne();
        assertThat(subject.diff("core", "rsync", 60).size()).isZero();
    }

    @Test
    public void test_get_repository_info() {
        repositories.updateByTag("rsync", now, Stream.of(object));
        var info = subject.getInfo("rsync", 0);

        assertThat(info).hasValue(new PublishedObjectStatusController.RepositoryInfo("rsync", "rsync://rpki.ripe.net", "RSYNC", 1));
    }

    @Test
    public void test_get_repository_objects() {
        repositories.updateByTag("core", now, Stream.of(object));
        var objects = subject.getObject("core", object.getUri(), 0);
        assertThat(objects).hasValue(Set.of(object));
    }

    @Test
    public void test_inspect_repository_objects() {
        repositories.updateByTag("core", now, Stream.of(object));
        var objects = subject.inspectObject("core", object.getUri());
        assertThat(objects.get().size()).isOne();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_get_rsync_diff() {
        var diff = (Map<PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>>)mock(Map.class);
        when(publishedObjectsSummary.getDiff(
                any(Instant.class),
                eq(repositories.trackersOfType(RepositoryTracker.Type.CORE)),
                eq(repositories.trackersOfType(RepositoryTracker.Type.RSYNC))
        )).thenReturn(diff);

        var result = subject.rsyncDiff();
        assertThat(result).isEqualTo(diff);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_get_rrdp_diff() {
        var diff = (Map< PublishedObjectsSummaryService.RepositoryDiffKey, Set<RepositoryEntry>>)mock(Map.class);
        when(publishedObjectsSummary.getDiff(
                any(Instant.class),
                eq(repositories.trackersOfType(RepositoryTracker.Type.CORE)),
                eq(repositories.trackersOfType(RepositoryTracker.Type.RRDP))
        )).thenReturn(diff);

        var result = subject.rrdpDiff();
        assertThat(result).isEqualTo(diff);
    }
}