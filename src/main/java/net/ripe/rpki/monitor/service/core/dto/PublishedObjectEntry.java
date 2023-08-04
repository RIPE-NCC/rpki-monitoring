package net.ripe.rpki.monitor.service.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;

@Builder
public record PublishedObjectEntry(@Getter String uri, @JsonProperty("sha256") String sha256Hex, Instant updatedAt) {
    public PublishedObjectEntry {
        Preconditions.checkArgument(sha256Hex != null && sha256Hex.length() == 64);
    }

    public byte[] sha256() {
        return HashCode.fromString(sha256Hex).asBytes();
    }
}
