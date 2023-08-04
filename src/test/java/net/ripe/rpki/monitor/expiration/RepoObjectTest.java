package net.ripe.rpki.monitor.expiration;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoObjectTest {
    final static byte[] EXAMPLE_SHA256 = Hashing.sha256().hashUnencodedChars("DEADBEEF").asBytes();
    final static String EXAMPLE_OBJECT_URI = "rsync://rpki.example.org/object.mft";

    @Test
    public void testRejectsInvalidLength() {
        var object = new RepoObject(Instant.now(), Instant.now(), EXAMPLE_OBJECT_URI, EXAMPLE_SHA256);
        assertThat(object.getSha256()).isEqualTo(HashCode.fromBytes(EXAMPLE_SHA256).toString());

        assertThatThrownBy(() -> new RepoObject(Instant.now(), Instant.now(), EXAMPLE_OBJECT_URI, new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void itShouldCompareByDate() {
        final var creation = Instant.now();
        final var expiration = creation.plus(Duration.ofDays(14));

        final RepoObject a = new RepoObject(creation, expiration, EXAMPLE_OBJECT_URI, EXAMPLE_SHA256);
        final RepoObject b = new RepoObject(expiration, expiration, EXAMPLE_OBJECT_URI, EXAMPLE_SHA256);

        assertThat(a).isLessThan(b);
        assertThat(b).isGreaterThan(a);

        final RepoObject c = new RepoObject(expiration, expiration.plusSeconds(1), EXAMPLE_OBJECT_URI, EXAMPLE_SHA256);

        assertThat(a).isLessThan(c);
        assertThat(c).isGreaterThan(a);
    }

    @Test
    public void itShouldCompareByUriIfTheDateAreTheSame() {
        final var creation = Instant.now();
        final var expiration = creation.plus(Duration.ofDays(14));

        final RepoObject a = new RepoObject(creation, expiration, "A", EXAMPLE_SHA256);
        final RepoObject b = new RepoObject(creation, expiration, "B", EXAMPLE_SHA256);

        assertThat(a).isLessThan(b);
        assertThat(b).isGreaterThan(a);
    }

    @Test
    public void itShouldCompareBySha256IfDateAndUriareTheSame() {
        final var creation = Instant.now();
        final var expiration = creation.plus(Duration.ofDays(14));

        // Use the highest 256-bit value possible
        var highestByteContent = new byte[32];
        for (int i=0; i<32; i++) { highestByteContent[i] = (byte) 0xff; }

        final RepoObject a = new RepoObject(creation, expiration, EXAMPLE_OBJECT_URI, EXAMPLE_SHA256);
        final RepoObject b = new RepoObject(creation, expiration, EXAMPLE_OBJECT_URI, highestByteContent);

        assertThat(a).isLessThan(b);
        assertThat(b).isGreaterThan(a);
    }
}