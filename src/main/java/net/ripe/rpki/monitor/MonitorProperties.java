package net.ripe.rpki.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
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
