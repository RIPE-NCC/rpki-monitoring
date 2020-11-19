package net.ripe.rpki.monitor.publishing.dto;

import net.ripe.rpki.monitor.HasHashAndUri;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileEntryTest {

    @Test
    public void testRelativeUrl() {
        assertEquals(
            FileEntry.withReativeUrl(getObj("rsync://server.com:5555/path/a/b/c")).getUri(),
            "/path/a/b/c"
        );

        assertEquals(
            FileEntry.withReativeUrl(getObj("https://rrdp.ripe.net/path/a/b/c")).getUri(),
            "/path/a/b/c"
        );

        assertEquals(
            FileEntry.withReativeUrl(getObj("/path/a/b/c")).getUri(),
            "/path/a/b/c"
        );
    }

    @Test
    public void testRelativeComparison() {
        final FileEntry fileEntry1 = FileEntry.withReativeUrl(getObj("rsync://server.com:5555/path/a/b/c"));
        final FileEntry fileEntry2 = FileEntry.withReativeUrl(getObj("rsync://server-2.com/path/a/b/c"));
        assertEquals(fileEntry1, fileEntry2);
    }


    private HasHashAndUri getObj(String url) {
        return new HasHashAndUri() {
            @Override
            public String getSha256() {
                return "aaaa";
            }

            @Override
            public String getUri() {
                return url;
            }
        };
    }
}