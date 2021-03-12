package net.ripe.rpki.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties("rrdp")
@Data
public class RrdpConfig {
    private String url;
    private List<String> otherUrls;
    private Duration interval;
}
