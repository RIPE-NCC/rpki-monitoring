package net.ripe.rpki.monitor.service.core.dto;

import lombok.Builder;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;

@Builder
@Value
public class PublishedObjectEntry implements HasHashAndUri {
    String uri;
    String sha256;
    Instant updatedAt;
}
