package net.ripe.rpki.monitor.config;

import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("expiry")
public record ExpiryMonitoringConfig(List<Matcher> match, Set<String> trackedTags) {

    /**
     * Track a repository when none are configured, or it is allow-listed.
     */
    public boolean shouldTrack(RepositoryTracker repoTracker) {
        return trackedTags == null || trackedTags.contains(repoTracker.getTag());
    }
    public record Matcher(@NonNull String regex, Duration threshold, @NonNull Map<String, String> labels) {
        public List<Tag> tags() {
            return labels.entrySet().stream().map(entry -> Tag.of(entry.getKey(), entry.getValue())).toList();
        }
    }
}
