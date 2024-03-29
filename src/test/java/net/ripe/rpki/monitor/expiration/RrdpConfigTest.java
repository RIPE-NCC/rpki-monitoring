package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.config.RrdpConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

public class RrdpConfigTest {
    @Test
    public void testOverrideHostname_not_set() {
        final var baseUrl = "https://rrdp.int.example.org/";
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", baseUrl + "notification.xml", null, Map.of(), Duration.ZERO);

        then(config.overrideHostname(baseUrl + "12343/1/delta.xml")).isEqualTo(baseUrl + "12343/1/delta.xml");
        then(config.metricUrlTag())
                .doesNotContain("@");
    }

    @Test
    public void testOverrideHostname_different_host() {
        final var baseUrl = "https://rrdp.other.host.example.org/";
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", baseUrl + "notification.xml", "rrdp.example.org", Map.of(), Duration.ZERO);

        then(config.overrideHostname("https://rrdp.other.host.example.org/12343/1/delta.xml")).isEqualTo("https://rrdp.example.org/12343/1/delta.xml");
        then(config.metricUrlTag())
                .contains("https://rrdp.other.host.example.org/notification.xml")
                .contains("@rrdp.example.org")
                .doesNotContain("=");
    }

    @Test
    public void testOverrideHostname_broken_urls() {
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", "https://rrdp.example.org/notification.xml", "rrdp.example.org", Map.of(), Duration.ZERO);
        final var brokenUrl = "https:/\\\\$/rrdp.other.host.example.org/12343/1/delta.xml";
        then(config.overrideHostname(brokenUrl)).isEqualTo(brokenUrl);
        then(config.overrideHostname(null)).isNull();
    }

    @Test
    public void testConnectTo() {
        final var baseUrl = "https://rrdp.example.org/";
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", baseUrl + "notification.xml", null, Map.of("rrdp.example.org", "rrdp.example.org.cnd.example.org"), Duration.ZERO);

        then(config.metricUrlTag())
                .contains("@rrdp.example.org=rrdp.example.org.cnd.example.org");
    }

}