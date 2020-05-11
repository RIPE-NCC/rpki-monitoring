package net.ripe.rpki.monitor.service.core.dto;

import lombok.Builder;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.net.URI;
import java.time.Instant;

@Builder
@Value
public class PublishedObject implements HasHashAndUri {
    private String uri;
    private Instant updatedAt;
    private String sha256;
}
