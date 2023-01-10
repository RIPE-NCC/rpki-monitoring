package net.ripe.rpki.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("rpkimonitor")
public class MonitorProperties {
    /** The api key header */
    private String internalApiKeyHeader;

    /** The git revision */
    private String version;
}
