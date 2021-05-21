package net.ripe.rpki.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Component
@ConfigurationProperties("rrdp")
@Data
public class RrdpConfig {
    private Duration interval;
    private String mainUrl;
    private Map<String, String> otherUrls = Collections.emptyMap();
}
