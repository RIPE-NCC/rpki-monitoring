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

    private static sealed class BaseFetcherMetrics {
        final Counter successfulUpdates;
        private final Counter failedUpdates;

        BaseFetcherMetrics(final String url, MeterRegistry meterRegistry) {
            successfulUpdates = buildCounter(url, "success", meterRegistry);
            failedUpdates = buildCounter(url, "failed", meterRegistry);
        }

        private static Counter buildCounter(String url, String statusTag, MeterRegistry registry) {
            return Counter.builder("rpkimonitoring.fetcher.updated")
                    .description("Number of fetches from the given URL")
                    .tag("status", statusTag)
                    .tag("url", url)
                    .register(registry);
        }

        public void failure() { this.failedUpdates.increment(); }
    }

    public static final class RsyncFetcherMetrics extends BaseFetcherMetrics {
        private RsyncFetcherMetrics(String url, MeterRegistry meterRegistry) {
            super(url, meterRegistry);
        }

        public void success() { this.successfulUpdates.increment(); }
    }

    public static final class RRDPFetcherMetrics extends BaseFetcherMetrics {
        final AtomicInteger rrdpSerial = new AtomicInteger();
        final AtomicInteger rrdpCollisions = new AtomicInteger();

        private RRDPFetcherMetrics(final String url, MeterRegistry meterRegistry) {
            super(url, meterRegistry);

            Gauge.builder("rpkimonitoring.fetcher.rrdp.serial", rrdpSerial::get)
                    .description("Serial of the RRDP notification.xml at the given URL")
                    .tag("url", url)
                    .register(meterRegistry);
            Gauge.builder("rpkimonitoring.fetcher.rrdp.url-collisions", rrdpCollisions::get)
                    .description("Number of objects with colliding URLs")
                    .tag("url", url)
                    .register(meterRegistry);
        }

        /** RRDP variant only can track a succesful update if it also provides a serial. */
        public void success(int serial, int collisionCount) {
            this.successfulUpdates.increment();
            this.rrdpSerial.set(serial);
            this.rrdpCollisions.set(collisionCount);
        }
    }
}
