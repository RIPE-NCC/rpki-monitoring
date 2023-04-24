package net.ripe.rpki.monitor.expiration;

import com.google.common.base.Preconditions;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.config.ExpiryMonitoringConfig;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
public class ExpiryMonitorHooks {
    @NonNull
    private final ExpiryMonitoringConfig expiryMonitoring;

    @NonNull
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<TrackedMonitor, MatcherMetrics> metrics = new ConcurrentHashMap<>();

    @Autowired
    public ExpiryMonitorHooks(ExpiryMonitoringConfig expiryMonitoring, MeterRegistry meterRegistry) {
        this.expiryMonitoring = expiryMonitoring;
        this.meterRegistry = meterRegistry;
    }

    public void track(final RepositoryTracker tracker) {
        // Track specified tags if set, but default to all of them.
        if (expiryMonitoring.shouldTrack(tracker)) {
            log.info("track({}, {}, {})", tracker.getUrl(), tracker.getTag(), tracker.getType());
            expiryMonitoring.match().stream().parallel().forEach(matcher -> {
                var matcherMetrics = metrics.computeIfAbsent(new TrackedMonitor(tracker.getTag(), tracker.getUrl(), matcher), monitor -> new MatcherMetrics(monitor, meterRegistry));

                matcherMetrics.trackObjects(tracker.view(Instant.now()).entries());
            });
        }
    }

    private record TrackedMonitor(String key, String url, ExpiryMonitoringConfig.Matcher matcher) { }

    private static class MatcherMetrics {
        private final AtomicInteger aboveGauge = new AtomicInteger();
        private final AtomicInteger belowGauge = new AtomicInteger();
        private final AtomicInteger unknownGauge = new AtomicInteger();

        private final long threshold;

        private final Predicate<String> regexMatch;

        MatcherMetrics(TrackedMonitor monitor, final MeterRegistry registry) {
            threshold = monitor.matcher.threshold().toSeconds();
            regexMatch = Pattern.compile(monitor.matcher.regex()).asMatchPredicate();

            BiConsumer<AtomicInteger, String> buildGauge = (value, comparison) ->
                Gauge.builder("rpkimonitoring.expiry.matcher", value::get)
                    .baseUnit("objects")
                    .description("Number of objects matching by regex, and comparison status (above=ok, below=error state, unknown=no expiration time)")
                    .tags(monitor.matcher.tags())
                    .tag("uri", monitor.url)
                    .tag("key", monitor.key)
                    .tag("regex", monitor.matcher.regex())
                    .tag("comparison", comparison)
                    .register(registry);

            buildGauge.accept(aboveGauge, "above");
            buildGauge.accept(belowGauge, "below");
            buildGauge.accept(unknownGauge,"unknown");
        }

        private boolean testRepositoryEntry(RepositoryEntry subject) {
            return regexMatch.test(subject.getUri());
        }

        public void trackObjects(Stream<RepositoryEntry> entries) {
            Preconditions.checkArgument(!entries.isParallel(), "regular expression is not thread safe.");
            // Process all entries, and set the gauge to the new value at the end.
            var above = new AtomicInteger();
            var below = new AtomicInteger();
            var unknown = new AtomicInteger();

            var unixTimeSeconds = Instant.now().getEpochSecond();

            entries.filter(this::testRepositoryEntry).forEach(entry -> entry.getExpiration().ifPresentOrElse(expiration -> {
                var secondsLeft = expiration.getEpochSecond()-unixTimeSeconds;

                (secondsLeft < threshold ? below : above).incrementAndGet();
            }, unknownGauge::incrementAndGet));

            aboveGauge.set(above.get());
            belowGauge.set(below.get());
            unknownGauge.set(unknown.get());
        }
    }
}
