package net.ripe.rpki.monitor.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties("object-filter")
@Data
@NoArgsConstructor
public class ObjectFilterConfig {
    private List<String> hashes;
    private List<String> urls;

    public static boolean ignoreObject(AppConfig appConfig, String uri, String hash) {
        if (appConfig == null) {
            return false;
        }
        if (appConfig.getObjectFilterConfig() == null) {
            return false;
        }
        return appConfig.getObjectFilterConfig().ignore(uri, hash);
    }

    private boolean ignore(String objectUri, String hash) {
        return (urls != null && urls.contains(objectUri))
                || (hashes != null && hashes.contains(hash));
    }
}
