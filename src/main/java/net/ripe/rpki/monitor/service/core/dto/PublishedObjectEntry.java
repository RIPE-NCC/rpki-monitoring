package net.ripe.rpki.monitor.service.core.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;

@Builder
public record PublishedObjectEntry(@Getter String uri, byte[] sha256, Instant updatedAt) implements HasHashAndUri {
}
