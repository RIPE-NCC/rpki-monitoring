package net.ripe.rpki.monitor.publishing.dto;

import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
public class FileEntry {
    private String uri;

     // hex representation of sha256 hash
    private String sha256;

    public static FileEntry from(HasHashAndUri obj) {
        return new FileEntry(obj.getUri(), obj.getSha256());
    }

    public static FileEntry withPath(HasHashAndUri obj) {
        final URI uri = URI.create(obj.getUri());
        return new FileEntry(uri.getPath(), obj.getSha256());
    }

    public static <T extends HasHashAndUri> Set<FileEntry> fromObjects(Collection<T> inp) {
        return makeEntries(inp, FileEntry::from);
    }

    public static <T extends HasHashAndUri> Set<FileEntry> fromObjectsWithUrlPath(Collection<T> inp) {
        return makeEntries(inp, FileEntry::withPath);
    }

    private static <T extends HasHashAndUri> Set<FileEntry> makeEntries(Collection<T> inp, Function<T, FileEntry> f) {
        return inp.parallelStream()
            .map(f)
            .collect(Collectors.toUnmodifiableSet());
    }
}
