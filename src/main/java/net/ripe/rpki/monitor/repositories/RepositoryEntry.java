package net.ripe.rpki.monitor.repositories;

import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

@Value
public class RepositoryEntry implements HasHashAndUri {
    String uri;
    String sha256;

    public static RepositoryEntry from(HasHashAndUri x) {
        return new RepositoryEntry(x.getUri(), x.getSha256());
    }
}
