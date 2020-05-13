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
    public void itShouldCompareByBytesIfDateAndUriareTheSame() {
        final Date expiration = new Date();
        final var uri = "rsync://example.org/";
        final RepoObject a = new RepoObject(expiration, uri, "A".getBytes());
        final RepoObject b = new RepoObject(expiration, uri, "B".getBytes());

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }
}