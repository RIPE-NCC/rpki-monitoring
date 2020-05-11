package net.ripe.rpki.monitor.publishing.dto;

import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

@Value
public class FileEntry {
    private String uri;
    /** hex representation of sha256 hash */
    private String sha256;

    public static FileEntry from(HasHashAndUri obj) {
        return new FileEntry(obj.getUri(), obj.getSha256());
    }
}
