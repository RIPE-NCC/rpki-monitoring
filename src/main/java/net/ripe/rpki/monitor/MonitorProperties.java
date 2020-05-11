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
    public final static String INTERNAL_API_KEY_HEADER = "X-API_KEY";

    private Core core;

    /** The git revision */
    private String version;

    @Data
    public static class Core {
        private String url;
        private String apiKey;
    }
}
