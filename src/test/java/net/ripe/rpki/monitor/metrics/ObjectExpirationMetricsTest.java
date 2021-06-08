package net.ripe.rpki.monitor.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.NoArgsConstructor;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;


@NoArgsConstructor
public class ObjectExpirationMetricsTest {
    private MeterRegistry meterRegistry;

    public final static String REPO_URL = "rsync://rpki.example.org/";
    private ObjectExpirationMetrics subject;

    @BeforeEach
    public void beforeEach() {
        meterRegistry = new SimpleMeterRegistry();
        subject = new ObjectExpirationMetrics(meterRegistry);
    }

    @Test
    public void itShouldHaveTimeToExpiryMetricsWhichCountedAllObjects() throws Exception {
        var now = Instant.now();
        var objects = Set.of(
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(Duration.ofMinutes(50)))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(Duration.ofMinutes(61)))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(Duration.ofHours(7).plusMinutes(10)))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(Duration.ofHours(14))))
        );

        subject.trackExpiration(REPO_URL, now, objects.stream().map(RepositoryEntry::from).collect(Collectors.toList()));

        final var buckets = Arrays.asList(meterRegistry.get(ObjectExpirationMetrics.COLLECTOR_EXPIRATION_METRIC)
                .tag("url", REPO_URL)
                .summary()
                .takeSnapshot()
                .histogramCounts());

        // Bucket durations are constants for the SLO
        then(buckets.stream().filter(bucket -> (int)bucket.bucket() == Duration.ofHours(1).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 1); // one object < 1 hr

        then(buckets.stream().filter(bucket -> (int)bucket.bucket() == Duration.ofHours(4).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 2); // two objects < 4 hrs

        then(buckets.stream().filter(bucket -> (int)bucket.bucket() == Duration.ofHours(7).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 2); // two objects < 7 hours

        then(buckets.stream().filter(bucket -> bucket.bucket() == TimeUnit.HOURS.toSeconds(8)))
                .first()
                .matches(bucket -> bucket.count() == 3); // three objects < 8 hours
    }

    @Test
    public void itShouldHaveTimeSinceCreationMetricsWhichCountedAllObjects() throws Exception {
        var now = Instant.now();
        var objects = Set.of(
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.minus(90, ChronoUnit.MINUTES))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.minus(18L * 31, ChronoUnit.DAYS))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.minus(18, ChronoUnit.HOURS)))
        );

        subject.trackExpiration(REPO_URL, now, objects.stream().map(RepositoryEntry::from).collect(Collectors.toList()));

        final var snapshot = meterRegistry.get(ObjectExpirationMetrics.COLLECTOR_CREATION_METRIC)
                .tag("url", REPO_URL)
                .summary()
                .takeSnapshot();

        then(snapshot.count()).isEqualTo(3);

        // Bucket durations are constants for the SLO
        then(Arrays.stream(snapshot.histogramCounts())
                .filter(bucket -> (int)bucket.bucket() == Duration.ofHours(1).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 0); // one object < 1 hr
        then(Arrays.stream(snapshot.histogramCounts())
                .filter(bucket -> (int)bucket.bucket() == Duration.ofHours(4).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 1); // one object within 4 hrs.
        then(Arrays.stream(snapshot.histogramCounts())
                .filter(bucket -> (int)bucket.bucket() == Duration.ofHours(24).toSeconds()))
                .first()
                .matches(bucket -> bucket.count() == 2); // two objects within 24hr
    }
}
