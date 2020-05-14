package net.ripe.rpki.monitor.service.core.dto;

import lombok.Builder;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;

@Builder
@Value
public class PublishedObjectEntry implements HasHashAndUri {
    private String uri;
    private Instant updatedAt;
    private String sha256;
}
