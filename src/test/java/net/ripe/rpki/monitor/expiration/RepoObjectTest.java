package net.ripe.rpki.monitor.expiration;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoObjectTest {


    @Test
    public void itShouldCompareByDate() {
        final RepoObject a = new RepoObject(new Date(), "A", "DEADBEEF".getBytes());
        final RepoObject b = new RepoObject(new Date(), "B", "FEEBDAED".getBytes());

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }

    @Test
    public void itShouldCompareByUriIfTheDateAreTheSame() {
        final Date expiration = new Date();
        final RepoObject a = new RepoObject(expiration, "A", "DEADBEEF".getBytes());
        final RepoObject b = new RepoObject(expiration, "B", "FEEBDAED".getBytes());

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }

    @Test
    public void itShouldCompareBySha256IfDateAndUriareTheSame() {
        final Date expiration = new Date();
        final var uri = "rsync://example.org/";

        // Note that the hash of "A" is smaller than that of "B":
        // $ echo "A" | shasum -a 256
        // > 06f961b802bc46ee168555f066d28f4f0e9afdf3f88174c1ee6f9de004fc30a0  -
        // $ echo "B" | shasum -a 256
        // >c0cde77fa8fef97d476c10aad3d2d54fcc2f336140d073651c2dcccf1e379fd6  -
        final RepoObject a = new RepoObject(expiration, uri, "A".getBytes());
        final RepoObject b = new RepoObject(expiration, uri, "B".getBytes());

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }
}