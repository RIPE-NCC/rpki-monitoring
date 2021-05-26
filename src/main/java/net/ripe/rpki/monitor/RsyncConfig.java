package net.ripe.rpki.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Component
@ConfigurationProperties("rsync")
@Data
public class RsyncConfig {
    private Duration interval;
    private int timeout;
    /**
     * URI of the main rsync repository.
     *
     * Will be used to override the URIs of objects from other repositories.
     */
    private String mainUrl;
    private Map<String, String> otherUrls = Collections.emptyMap();
}
