package net.ripe.rpki.monitor.repositories;

import lombok.*;
import lombok.experimental.Accessors;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@Value
@Builder
public class RepositoryEntry implements HasHashAndUri {
    @NonNull String uri;
    @NonNull byte[] sha256;
    @Builder.Default
    Optional<Instant> creation = Optional.empty();
    @Builder.Default
    Optional<Instant> expiration = Optional.empty();

    public static RepositoryEntry from(RepoObject x) {
        return new RepositoryEntry(
                x.getUri(),
                x.sha256(),
                Optional.ofNullable(x.creation()),
                Optional.ofNullable(x.expiration())
        );
    }

    public static RepositoryEntry from(PublishedObjectEntry x) {
        return new RepositoryEntry(
            x.getUri(),
            x.sha256(),
            Optional.empty(),
            Optional.empty()
        );
    }

    @Override
    public String getUri() {
        return uri;
    }
}
