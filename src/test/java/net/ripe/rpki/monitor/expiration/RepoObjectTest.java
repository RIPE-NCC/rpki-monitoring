package net.ripe.rpki.monitor.expiration;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoObjectTest {


    @Test
    public void itShouldCompareByDate() {
        final RepoObject a = new RepoObject(new Date(), "A");
        final RepoObject b = new RepoObject(new Date(), "B");

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }

    @Test
    public void itShouldCompareByUriIfTheDateAreTheSame() {
        final Date expiration = new Date();
        final RepoObject a = new RepoObject(expiration, "A");
        final RepoObject b = new RepoObject(expiration, "B");

        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }

}