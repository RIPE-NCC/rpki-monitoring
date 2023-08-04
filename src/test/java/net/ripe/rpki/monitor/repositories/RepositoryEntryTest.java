package net.ripe.rpki.monitor.repositories;

import com.google.common.hash.HashCode;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryEntryTest {
    final Instant now = Instant.now();

    @Test
    public void test_from_RepoObject() {
        var x = new RepoObject(
                now,
                now.plusSeconds(3600 * 12),
                "rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer",
                HashCode.fromString("cff9a7bde0feaeb8222c5c5d7c4ad628230c00941dccee895601ef0db5b3443c").asBytes()
        );
        var r = RepositoryEntry.from(x);
        assertThat(r.getUri()).isEqualTo(x.getUri());
        assertThat(r.getSha256()).isEqualTo(x.getSha256());
        assertThat(r.creation()).isEqualTo(Optional.of(x.creation()));
        assertThat(r.expiration()).isEqualTo(Optional.of(x.expiration()));
    }

    @Test
    public void test_from_PublishedObjectEntry() {
        var x = PublishedObjectEntry.builder()
                .uri("rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer")
                .sha256(HashCode.fromString("781c4689f8c8cf65cfc00241c3dc75cb697df340be425ff4c2378b5de720f258").asBytes())
                .build();
        var r = RepositoryEntry.from(x);
        assertThat(r.getUri()).isEqualTo(x.getUri());
        assertThat(r.getSha256()).isEqualTo(x.getSha256());
        assertThat(r.expiration()).isEqualTo(Optional.empty());
    }
}