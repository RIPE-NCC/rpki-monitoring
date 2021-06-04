package net.ripe.rpki.monitor.repositories;

import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoriesStateTest {
    private final RepositoriesState state = RepositoriesState.init(List.of(
            Triple.of("rrdp.ripe.net", "https://rrdp.ripe.net/", RepositoryTracker.Type.RRDP),
            Triple.of("rpki.ripe.net", "rsync://rpki.ripe.net/", RepositoryTracker.Type.RSYNC)
    ));

    @Test
    public void test_state_update() {
        var now = Instant.now();
        var entry = PublishedObjectEntry.builder().sha256("6248c142e1d153a4526f8709613cf4f1600d54d0a13c76d0ab4ed616aef5f0b4").build();
        var entries = List.of(entry);

        var tracker = state.updateByUrl("https://rrdp.ripe.net/", now, entries);
        assertThat(tracker.size(now)).isEqualTo(1);

        now = now.plusSeconds(300);
        var tracker_ = state.updateByUrl("https://rrdp.ripe.net/", now, List.of());
        assertThat(tracker_.size(now)).isEqualTo(0);
    }

    @Test
    public void test_update_state_hooks() {
        var now = Instant.now();

        var called = new AtomicInteger(0);
        state.addHook((tracker) -> called.incrementAndGet());
        state.updateByUrl("https://rrdp.ripe.net/", now, List.of());
        assertThat(called.get()).isEqualTo(1);
    }

    @Test
    public void get_tracker_by_tag_should_return_tracker() {
        var tracker = state.getTrackerByTag("rrdp.ripe.net");
        assertThat(tracker).isPresent();
    }

    @Test
    public void get_tracker_by_url_should_return_tracker() {
        var tracker = state.getTrackerByUrl("https://rrdp.ripe.net/");
        assertThat(tracker).isPresent();
    }

    @Test
    public void test_get_all_trackers() {
        var trackers = state.allTrackers();
        assertThat(trackers).hasSize(2);
    }

    @Test
    public void test_get_trackers_by_type() {
        var trackers = state.trackersOfType(RepositoryTracker.Type.RRDP);
        assertThat(trackers).hasSize(1);
        assertThat(trackers.get(0).getTag()).isEqualTo("rrdp.ripe.net");
    }

    @Test
    public void test_get_other_trackers() {
        var tracker = state.getTrackerByTag("rrdp.ripe.net");
        var others = state.otherTrackers(tracker.get());
        assertThat(others).hasSize(1);
        assertThat(others.get(0).getTag()).isEqualTo("rpki.ripe.net");
    }
}