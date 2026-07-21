package net.ripe.rpki.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("object-filter")
public record ObjectFilterConfig(List<String> hashes, List<String> urls) {

    public boolean ignore(String objectUri, String hash) {
        return (urls != null && urls.contains(objectUri))
                || (hashes != null && hashes.contains(hash));
    }

    public static ObjectFilterConfig empty() {
        return new ObjectFilterConfig(List.of(), List.of());
    }
}
