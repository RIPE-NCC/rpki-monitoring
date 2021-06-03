package net.ripe.rpki.monitor.repositories;

import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoriesStateTest {
    private final RepositoriesState state = RepositoriesState.init(List.of(
            Pair.of("rrdp.ripe.net", "https://rrdp.ripe.net/"),
            Pair.of("rpki.ripe.net", "rsync://rpki.ripe.net/")
    ));

    @Test
    public void test_state_update() {
        var now = Instant.now();
        var entry = PublishedObjectEntry.builder().sha256("6248c142e1d153a4526f8709613cf4f1600d54d0a13c76d0ab4ed616aef5f0b4").build();
        var entries = List.of(entry);

        var tracker = state.updateByTag("rrdp.ripe.net", now, entries);
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
        state.updateByTag("rrdp.ripe.net", now, List.of());
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
    public void get_other_trackers() {
        var tracker = state.getTrackerByTag("rrdp.ripe.net");
        var others = state.getOtherTrackers(tracker.get());
        assertThat(others).hasSize(1);
        assertThat(others.get(0).getTag()).isEqualTo("rpki.ripe.net");
    }
}