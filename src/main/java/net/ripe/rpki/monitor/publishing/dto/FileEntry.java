package net.ripe.rpki.monitor.publishing.dto;

import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Value
public class FileEntry {
    private String uri;
    /** hex representation of sha256 hash */
    private String sha256;

    public static FileEntry from(HasHashAndUri obj) {
        return new FileEntry(obj.getUri(), obj.getSha256());
    }

    public static <T extends HasHashAndUri>Set<FileEntry> fromObjects(Collection<T> inp) {
        return inp.stream()
                .map(FileEntry::from)
                .collect(Collectors.toUnmodifiableSet());
    }
}
