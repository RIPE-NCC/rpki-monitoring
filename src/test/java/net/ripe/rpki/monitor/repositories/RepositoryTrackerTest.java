package net.ripe.rpki.monitor.repositories;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTrackerTest {

    private final Instant t = Instant.now();

    @Nested
    class View {
        @Test
        public void test_size() {
            var object = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "0cf7574c4cfbe49b68fa4817828225592c4ef9a538ad617d42de26f6cc349b33",
                    Optional.of(t),
                    Optional.empty()
            );
            var repo = RepositoryTracker.empty("tag", "https://example.com", RepositoryTracker.Type.CORE, Duration.ofSeconds(3600));
            repo.update(t, Stream.of(object));

            assertThat(repo.view(t.minusSeconds(1)).size()).isZero();
            assertThat(repo.view(t).size()).isOne();
        }

        @Test
        public void test_first_seen() {
            var object = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "a6c0ffd45b7a799fbd1a303cb4322a1387e74cb22b80c9c54ed4378f22a81f0f",
                    Optional.of(t),
                    Optional.empty()
            );
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t.minusSeconds(300), Stream.of(object), Duration.ofSeconds(3600));

            repo.update(t, Stream.of(object));

            assertThat(repo.view(t.minusSeconds(600)).size()).isZero();
            assertThat(repo.view(t.minusSeconds(300)).size()).isOne();
            assertThat(repo.view(t).size()).isOne();
        }

        @Test
        public void test_disposal() {
            var obj1 = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "a6c0ffd45b7a799fbd1a303cb4322a1387e74cb22b80c9c54ed4378f22a81f0f",
                    Optional.of(t),
                    Optional.empty()
            );
            var obj2 = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "45cdf3f6082774e19fecf80817863c39e29a5a3646746125c55ed3209d3508ea",
                    Optional.of(t),
                    Optional.empty()
            );
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t.minusSeconds(300), Stream.of(obj1, obj2), Duration.ofSeconds(3600));

            repo.update(t, Stream.of(obj1, obj2));
            repo.update(t.plusSeconds(300), Stream.of(obj2));

            assertThat(repo.view(t.minusSeconds(300)).size()).isEqualTo(2);
            assertThat(repo.view(t).size()).isEqualTo(2);
            assertThat(repo.view(t.plusSeconds(300)).size()).isOne();
        }

        @Test
        public void test_get_object() {
            var object = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "45cdf3f6082774e19fecf80817863c39e29a5a3646746125c55ed3209d3508ea",
                    Optional.of(t),
                    Optional.empty()
            );
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(object), Duration.ofSeconds(3600));
            var view = repo.view(t);

            assertThat(view.getObject(object.getSha256())).isEqualTo(Optional.of(object));
            assertThat(view.getObject("unknown")).isEmpty();
        }

        @Test
        public void test_has_object() {
            var object = new RepositoryEntry(
                    "rsync://example.com/repository/DEFAULT/xyz.cer",
                    "6b0b3985e254bcb00c0e20ad09747ac4799f294f2ebcce7d4d805e452e3297a1",
                    Optional.of(t),
                    Optional.of(t.plusSeconds(3600))
            );
            var noTimestamps = new RepositoryEntry(object.getUri(), object.getSha256(), Optional.empty(), Optional.empty());
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(object), Duration.ofSeconds(3600));
            var view = repo.view(t);

            assertThat(view.hasObject(object)).isTrue();
            assertThat(view.hasObject(noTimestamps)).isTrue();
            assertThat(view.hasObject(RepositoryEntry.builder().sha256("unknown").uri(object.getUri()).build())).isFalse();
        }
    }

    @Nested
    class Expiration {
        RepositoryEntry openEnded = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "6cfe9a0498cf15254b85f8b03fa727ec2d528fe590d2a202428542435a0a62f7",
                Optional.of(t),
                Optional.empty()
        );
        RepositoryEntry expiresInOneHour = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "6cfe9a0498cf15254b85f8b03fa727ec2d528fe590d2a202428542435a0a62f7",
                Optional.of(t),
                Optional.of(t.plusSeconds(3600))
        );

        @Test
        public void test_open_ended_objects_dont_expire() {
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(openEnded), Duration.ofSeconds(3600));

            var result = repo.view(t).expiration(t.plusSeconds(3600 * 24));
            assertThat(result).hasSize(0);
        }

        @Test
        public void test_fixed_ended_object_expiration() {
            var repo = RepositoryTracker.with("tag", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(expiresInOneHour), Duration.ofSeconds(3600));

            var result = repo.view(t).expiration(t.plusSeconds(3600 * 24));
            assertThat(result).isEqualTo(Set.of(expiresInOneHour));
        }
    }

    @Nested
    class Difference {
        RepositoryEntry newObject = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "02c2a8e60fde7d630eb3b44c61b1b0326d6f664c9d3d4be6f5cef4393f8bd468",
                Optional.of(t),
                Optional.empty()
        );
        RepositoryEntry oldObject = new RepositoryEntry(
                "rsync://example.com/repository/DEFAULT/xyz.cer",
                "fbb8cef9856ddd08d351c2647dac92354ca61600f52bb56b7b0af7d504ee6ea4",
                Optional.of(t),
                Optional.empty()
        );

        @Test
        public void test_same_repo() {
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(newObject), Duration.ofSeconds(3600));

            assertThat(core.difference(core, t, Duration.ZERO)).isEmpty();
        }

        @Test
        public void test_different_object_hash() {
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(oldObject, newObject), Duration.ofSeconds(3600));
            var rsync = RepositoryTracker.with("rsync", "rsync://example.com", RepositoryTracker.Type.RSYNC, t, Stream.of(oldObject), Duration.ofSeconds(3600));

            assertThat(core.difference(rsync, t, Duration.ZERO)).isEqualTo(Set.of(newObject));
        }

        @Test
        public void test_threshold() {
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t.minusSeconds(300), Stream.of(oldObject), Duration.ofSeconds(3600));
            var rsync = RepositoryTracker.with("rsync", "rsync://example.com", RepositoryTracker.Type.RSYNC, t, Stream.of(oldObject), Duration.ofSeconds(3600));

            core.update(t, Stream.of(oldObject, newObject));

            assertThat(core.difference(rsync, t, Duration.ofSeconds(300))).isEmpty();
            assertThat(core.difference(rsync, t, Duration.ZERO)).isEqualTo(Set.of(newObject));
        }

        @Test
        public void test_ignore_object_timestamps() {
            var coreObject = new RepositoryEntry(newObject.getUri(), newObject.getSha256(),Optional.empty(), Optional.empty());
            var rrdpObject = new RepositoryEntry(newObject.getUri(), newObject.getSha256(), Optional.of(t), Optional.of(t.plusSeconds(3600 * 8)));
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(coreObject), Duration.ofSeconds(3600));
            var rrdp = RepositoryTracker.with("rrdp", "https://example.com", RepositoryTracker.Type.RRDP, t, Stream.of(rrdpObject), Duration.ofSeconds(3600));

            assertThat(core.difference(rrdp, t, Duration.ZERO)).isEmpty();
        }

        @Test
        public void test_object_disposed_after_timestamp() {
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(oldObject), Duration.ofSeconds(3600));
            var rrdp = RepositoryTracker.with("rrdp", "https://example.com", RepositoryTracker.Type.RRDP, t, Stream.of(oldObject), Duration.ofSeconds(3600));

            core.update(t.plusSeconds(1) , Stream.of(newObject));

            assertThat(core.difference(rrdp, t, Duration.ZERO)).isEmpty();
            assertThat(core.difference(rrdp, t.plusSeconds(1), Duration.ZERO)).hasSize(1);
        }

        @Test
        public void test_object_disposed_and_deleted() {
            var core = RepositoryTracker.with("core", "https://example.com", RepositoryTracker.Type.CORE, t, Stream.of(oldObject), Duration.ZERO);
            var rrdp = RepositoryTracker.with("rrdp", "https://example.com", RepositoryTracker.Type.RRDP, t, Stream.of(oldObject), Duration.ZERO);

            core.update(t.plusSeconds(1) , Stream.of(newObject)); // first-seen of newObject, oldObject disposed
            core.update(t.plusSeconds(2) , Stream.of(newObject)); // delete disposed oldObject

            assertThat(core.difference(rrdp, t.plusSeconds(1), Duration.ZERO)).hasSize(1);;
        }
    }
}
