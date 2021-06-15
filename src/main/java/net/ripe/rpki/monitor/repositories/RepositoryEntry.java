package net.ripe.rpki.monitor.repositories;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Value
@AllArgsConstructor
@Builder
public class RepositoryEntry implements HasHashAndUri {
    @NonNull String uri;
    @NonNull String sha256;
    @Builder.Default
    Optional<Instant> creation = Optional.empty();
    @Builder.Default
    Optional<Instant> expiration = Optional.empty();

    public static RepositoryEntry from(RepoObject x) {
        return new RepositoryEntry(
                x.getUri(),
                x.getSha256(),
                Optional.ofNullable(x.getCreation()).map(Date::toInstant),
                Optional.ofNullable(x.getExpiration()).map(Date::toInstant)
        );
    }

    public static RepositoryEntry from(PublishedObjectEntry x) {
        return new RepositoryEntry(x.getUri(), x.getSha256(), Optional.empty(), Optional.empty());
    }
}
