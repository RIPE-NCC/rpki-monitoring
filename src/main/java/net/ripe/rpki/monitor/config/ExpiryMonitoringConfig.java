package net.ripe.rpki.monitor.config;

import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("expiry")
public record ExpiryMonitoringConfig(List<Matcher> match) {

    public record Matcher(@NonNull String regex, Duration threshold, @NonNull Map<String, String> labels) {
        public List<Tag> tags() {
            return labels.entrySet().stream().map(entry -> Tag.of(entry.getKey(), entry.getValue())).toList();
        }
    }
}
