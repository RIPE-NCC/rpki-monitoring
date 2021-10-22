package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.RrdpConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class RrdpConfigTest {
    @Test
    public void testOverrideHostname_not_set() {
        final var baseUrl = "https://rrdp.int.example.org/";
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", baseUrl + "notification.xml", null);

        then(config.overrideHostname(baseUrl + "12343/1/delta.xml")).isEqualTo(baseUrl + "12343/1/delta.xml");
    }

    @Test
    public void testOverrideHostname_different_host() {
        final var baseUrl = "https://rrdp.example.org/";
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", baseUrl + "notification.xml", "rrdp.example.org");

        then(config.overrideHostname("https://rrdp.other.host.example.org/12343/1/delta.xml")).isEqualTo(baseUrl + "12343/1/delta.xml");
    }

    @Test
    public void testOverrideHostname_broken_urls() {
        final var config = new RrdpConfig.RrdpRepositoryConfig("test-config", "https://rrdp.example.org/notification.xml", "rrdp.example.org");
        final var brokenUrl = "https:/\\\\$/rrdp.other.host.example.org/12343/1/delta.xml";
        then(config.overrideHostname(brokenUrl)).isEqualTo(brokenUrl);
        then(config.overrideHostname(null)).isNull();
    }
}