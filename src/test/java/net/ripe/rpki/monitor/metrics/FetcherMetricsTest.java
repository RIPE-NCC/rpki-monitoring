package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fetcher metrics tests that just check that they are safe to use.
 */
class FetcherMetricsTest {
    FetcherMetrics subject;
    MeterRegistry registry;

    @BeforeEach
    public void setup() {
        registry = new SimpleMeterRegistry();
        subject = new FetcherMetrics(registry);
    }

    @Test
    void testRsyncMetrics() {
        var rsync1 = subject.rsync("rsync://rsync1.example.org");
        var rsync2 = subject.rsync("rsync://rsync2.example.org");
        var rsync3 = subject.rsync("rsync://rsync3.example.org");

        rsync1.success();
        rsync2.failure();
        rsync3.timeout();

        assertThat(updatedCountMetricValue("rsync://rsync1.example.org", "success")).isOne();
        assertThat(updatedCountMetricValue("rsync://rsync1.example.org", "failed")).isZero();
        assertThat(updatedCountMetricValue("rsync://rsync1.example.org", "timeout")).isZero();

        assertThat(updatedCountMetricValue("rsync://rsync2.example.org", "success")).isZero();
        assertThat(updatedCountMetricValue("rsync://rsync2.example.org", "failed")).isOne();
        assertThat(updatedCountMetricValue("rsync://rsync2.example.org", "timeout")).isZero();

        assertThat(updatedCountMetricValue("rsync://rsync3.example.org", "success")).isZero();
        assertThat(updatedCountMetricValue("rsync://rsync3.example.org", "failed")).isZero();
        assertThat(updatedCountMetricValue("rsync://rsync3.example.org", "timeout")).isOne();
    }

    @Test
    void testRRDPMetrics() {
        Function<String, Double> rrdpSerialMetricValue = (String url) -> registry.get("rpkimonitoring.fetcher.rrdp.serial").tag("url", url).gauge().value();

        var rrdp1 = subject.rrdp("https://rrdp1.example.org");
        var rrdp2 = subject.rrdp("https://rrdp2.example.org");
        var rrdp3 = subject.rrdp("https://rrdp3.example.org");

        rrdp1.success(1, 0);
        rrdp2.failure();
        rrdp3.timeout();

        assertThat(updatedCountMetricValue("https://rrdp1.example.org", "success")).isOne();
        assertThat(updatedCountMetricValue("https://rrdp1.example.org", "failed")).isZero();
        assertThat(updatedCountMetricValue("https://rrdp1.example.org", "timeout")).isZero();
        assertThat(rrdpSerialMetricValue.apply("https://rrdp1.example.org")).isOne();

        assertThat(updatedCountMetricValue("https://rrdp2.example.org", "success")).isZero();
        assertThat(updatedCountMetricValue("https://rrdp2.example.org", "failed")).isOne();
        assertThat(updatedCountMetricValue("https://rrdp2.example.org", "timeout")).isZero();
        assertThat(rrdpSerialMetricValue.apply("https://rrdp2.example.org")).isZero();

        assertThat(updatedCountMetricValue("https://rrdp3.example.org", "success")).isZero();
        assertThat(updatedCountMetricValue("https://rrdp3.example.org", "failed")).isZero();
        assertThat(updatedCountMetricValue("https://rrdp3.example.org", "timeout")).isOne();
        assertThat(rrdpSerialMetricValue.apply("https://rrdp3.example.org")).isZero();
    }

    private double updatedCountMetricValue(final String url, final String status) {
        return registry.get("rpkimonitoring.fetcher.updated").tag("status", status).tag("url", url).counter().count();
    }
}
