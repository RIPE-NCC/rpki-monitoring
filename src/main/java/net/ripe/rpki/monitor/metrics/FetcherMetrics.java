package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
@Component
public class FetcherMetrics {

    @Autowired
    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, RsyncFetcherMetrics> rsyncMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RRDPFetcherMetrics> rrdpMetrics = new ConcurrentHashMap<>();

    public RsyncFetcherMetrics rsync(String url) {
        return rsyncMetrics.computeIfAbsent(url, repoUrl -> new RsyncFetcherMetrics(repoUrl, registry));
    }

    public RRDPFetcherMetrics rrdp(String url) {
        return rrdpMetrics.computeIfAbsent(url, repoUrl -> new RRDPFetcherMetrics(repoUrl, registry));
    }

    private static Counter buildCounter(String url, String statusTag, MeterRegistry registry) {
        return Counter.builder("rpkimonitoring.fetcher.updated")
                .description("Number of fetches from the given URL")
                .tag("status", statusTag)
                .tag("url", url)
                .register(registry);
    }

    public record RsyncFetcherMetrics(Counter successfulUpdates, Counter failedUpdates) {
        public RsyncFetcherMetrics(final String url, MeterRegistry meterRegistry) {
            this(
                    buildCounter(url, "success", meterRegistry),
                    buildCounter(url, "failed", meterRegistry)
            );
        }

        public void failure() { this.failedUpdates.increment(); }
        public void success() { this.successfulUpdates.increment(); }
    }

    public record RRDPFetcherMetrics(Counter succesfulUpdates, Counter failedUpdates, AtomicInteger rrdpSerial) {
        public RRDPFetcherMetrics(final String url, MeterRegistry meterRegistry) {
            this(
                    buildCounter(url, "success", meterRegistry),
                    buildCounter(url, "failed", meterRegistry),
                    new AtomicInteger()
            );

            Gauge.builder("rpkimonitoring.fetcher.rrdp.serial", rrdpSerial::get)
                    .description("Serial of the RRDP notification.xml at the given URL")
                    .tag("url", url)
                    .register(meterRegistry);
        }

        public void failure() { this.failedUpdates.increment(); }

        public void success(int serial) {
            this.succesfulUpdates.increment();
            this.rrdpSerial.set(serial);
        }
    }
}
