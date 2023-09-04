package net.ripe.rpki.monitor.config;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
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
        private String overrideHostname = null;
        private Map<String, String> connectTo = Map.of();

        private Duration totalRequestTimeout = Duration.ofSeconds(60);

        public String metricUrlTag() {
            // Two aspects in the config affect the real repository we are tracking:
            //   * overriding the hostname in subsequent requests
            //   * connect-to support
            var urlTag = Strings.isNullOrEmpty(this.getOverrideHostname()) ? this.getNotificationUrl() : String.format("%s@%s", this.getNotificationUrl(), this.getOverrideHostname());
            var connectToTag = Joiner.on(", ").withKeyValueSeparator("=").join(this.getConnectTo());

            return this.getConnectTo().isEmpty() ? urlTag : "%s@%s".formatted(urlTag, connectToTag);
        }

        /**
         * Override the hostname in the given URL according to this config.
         * */
        public String overrideHostname(String url) {
            return Optional.ofNullable(overrideHostname).map(override -> rewriteHostInUrl(url, override)).orElse(url);
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
