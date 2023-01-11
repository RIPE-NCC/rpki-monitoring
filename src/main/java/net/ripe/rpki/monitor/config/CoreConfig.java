package net.ripe.rpki.monitor.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Component
@ConfigurationProperties("core")
@Data
@NoArgsConstructor
public class CoreConfig {
    private boolean enable = false;
    private @NonNull String url;
    private @NonNull String apiKey;
    private Duration interval = Duration.of(1, ChronoUnit.MINUTES);
    private Duration initialDelay = Duration.of(10, ChronoUnit.SECONDS);
}
