package net.ripe.rpki.monitor.repositories;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTrackerTest {
    @Test
    public void test_size_at_time() {
        var object = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "0cf7574c4cfbe49b68fa4817828225592c4ef9a538ad617d42de26f6cc349b33"
        );
        var repo = RepositoryTracker.empty("tag", "https://example.com", RepositoryTracker.Type.CORE);
        var t = Instant.now();
        repo.update(t, Set.of(object));

        assertThat(repo.size(t.minusSeconds(1))).isZero();
        assertThat(repo.size(t)).isOne();
    }

    @Test
    public void test_first_seen() {
        var object = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "a6c0ffd45b7a799fbd1a303cb4322a1387e74cb22b80c9c54ed4378f22a81f0f"
        );
        var t = Instant.now();
        var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t.minusSeconds(300), Set.of(object));

        core.update(t, Set.of(object));

        assertThat(core.size(t.minusSeconds(600))).isZero();
        assertThat(core.size(t.minusSeconds(300))).isOne();
        assertThat(core.size(t)).isOne();
    }

    @Nested
    class Difference {
        RepositoryEntry newObject = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "02c2a8e60fde7d630eb3b44c61b1b0326d6f664c9d3d4be6f5cef4393f8bd468"
        );
        RepositoryEntry oldObject = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "fbb8cef9856ddd08d351c2647dac92354ca61600f52bb56b7b0af7d504ee6ea4"
        );

        @Test
        public void test_same_repo() {
            var t = Instant.now();
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Set.of(newObject));

            assertThat(core.difference(core, t, Duration.ofSeconds(0))).isEmpty();
        }

        @Test
        public void test_different_object_hash() {
            var t = Instant.now();
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Set.of(oldObject, newObject));
            var rsync = RepositoryTracker.with("rsync", "rsync://example.com", RepositoryTracker.Type.RSYNC, t, Set.of(oldObject));

            assertThat(core.difference(rsync, t, Duration.ofSeconds(0))).isEqualTo(Set.of(newObject));
        }

        @Test
        public void test_threshold() {
            var t = Instant.now();
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t.minusSeconds(300), Set.of(oldObject));
            var rsync = RepositoryTracker.with("rsync", "rsync://example.com", RepositoryTracker.Type.RSYNC, t, Set.of(oldObject));

            core.update(t, Set.of(oldObject, newObject));

            assertThat(core.difference(rsync, t, Duration.ofSeconds(300))).isEmpty();
            assertThat(core.difference(rsync, t, Duration.ofSeconds(0))).isEqualTo(Set.of(newObject));
        }
    }
}