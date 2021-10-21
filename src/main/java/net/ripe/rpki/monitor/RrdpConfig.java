package net.ripe.rpki.monitor;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties("rrdp")
@Data
public class RrdpConfig {
    private Duration interval;
    private List<RrdpRepositoryConfig> targets;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class RrdpRepositoryConfig {
        private String name;
        private String notificationUrl;
        /**
         * Hostname to force in URLs in the XML
         * (Usage of optional is not recommended with @ConfigurationProperties [0])
         * [0]: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties.constructor-binding
         */
        private String overrideHostName = null;

        /** Rewrite a URL given this config */
        public String rewriteUrl(String url) {
            return Optional.ofNullable(overrideHostName).map(override -> rewriteHostInUrl(url, override)).orElse(url);
        }

        private static String rewriteHostInUrl(String inp, String overrideHostWith) {
            try {
                var originalUri = URI.create(inp);

                return new URI(
                        originalUri.getScheme(),
                        overrideHostWith,
                        originalUri.getPath(),
                        originalUri.getFragment()
                ).toString();
            } catch (NullPointerException | IllegalArgumentException | URISyntaxException e) {
                // Noop on illegal input
                return inp;
            }
        }
    }
}
