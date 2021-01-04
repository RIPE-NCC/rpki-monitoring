package net.ripe.rpki.monitor.publishing.dto;

import net.ripe.rpki.monitor.HasHashAndUri;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileEntryTest {

    @Test
    public void testUrlPath() {
        assertEquals(
            "/path/a/b/c",
            FileEntry.withPath(getObj("rsync://server.com:5555/path/a/b/c")).getUri()
        );

        assertEquals(
            "/path/a/b/c",
            FileEntry.withPath(getObj("https://rrdp.ripe.net/path/a/b/c")).getUri()
        );

        assertEquals(
            "/path/a/b/c",
            FileEntry.withPath(getObj("/path/a/b/c")).getUri()
        );
    }

    @Test
    public void testPathComparison() {
        final FileEntry fileEntry1 = FileEntry.withPath(getObj("rsync://server.com:5555/path/a/b/c"));
        final FileEntry fileEntry2 = FileEntry.withPath(getObj("rsync://server-2.com/path/a/b/c"));
        assertEquals(fileEntry1, fileEntry2);
    }


    private HasHashAndUri getObj(String url) {
        return new FileEntry(url, "aaaa");
    }
}