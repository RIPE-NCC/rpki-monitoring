package net.ripe.rpki.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties("rsync")
@Data
public class RsyncConfig {
    private Duration interval;
    private int timeout;
    /**
     * URI of the main rsync repository.
     *
     * Is not fetched by itself (without it also being in the targets), but will be used
     * to override the URIs of objects from other repositories.
     */
    private Path baseDirectory;
    private String repositoryUrl;
    private List<String> directories = List.of();
    private List<RsyncTarget> targets = List.of();

    public record RsyncTarget(String name, String url) {}
}
