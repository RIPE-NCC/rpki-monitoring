package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableMap;
import io.micrometer.tracing.Tracer;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpSnapshotClient;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import net.ripe.rpki.monitor.util.RrdpContent;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.ripe.rpki.monitor.util.RrdpContent.prefetch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AbstractObjectsAboutToExpireCollectorIT {
    private static RrdpSnapshotClient.RrdpSnapshotState rrdpSnapshot;

    private final RepositoriesState state = RepositoriesState.init(List.of(Triple.of("rrdp", "https://rrdp.ripe.net", RepositoryTracker.Type.RRDP)), Duration.ZERO);

    ObjectAndDateCollector collector = new ObjectAndDateCollector(
            new RepoFetcher() {
                @Override
                public ImmutableMap<String, RpkiObject> fetchObjects() throws FetcherException {
                    return rrdpSnapshot.objects();
                }

                @Override
                public Meta meta() {
                    return new Meta("prefeched", rrdpSnapshot.snapshotUrl());
                }
            },
            mock(CollectorUpdateMetrics.class),
            state,
            (objects) -> {},
            Tracer.NOOP,
            false
    );

    @BeforeAll
    static void fetchRrdpData() {
        rrdpSnapshot = prefetch(RrdpContent.TrustAnchor.RIPE);
        Assumptions.assumeTrue(rrdpSnapshot != null, "Could not fetch RIPE RRDP data set");
    }

    @Test
    public void itShouldCalculateMinimalExpirationSummary() throws ParseException {
        var objects = rrdpSnapshot.objects();

        var passed = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unknown = new AtomicInteger();
        var maxObjectSize = new AtomicInteger();

        var res = collector.calculateExpirationSummary(passed, rejected, unknown, maxObjectSize, objects).toList();

        assertThat(passed.get() + rejected.get() + unknown.get()).isEqualTo(objects.size());
        assertThat(maxObjectSize.get()).isGreaterThan(10_240).isLessThan(4_096_000);

        assertThat(res).hasSize(objects.size());

        // summary has distinct URIs and all URIs from input are present in output.
        var summaryUris = res.stream().map(x -> x.getUri()).collect(Collectors.toSet());
        assertThat(summaryUris).hasSize(objects.size());
        assertThat(summaryUris).allSatisfy(uri -> assertThat(objects.containsKey(uri)));

        // for all objects, the expiration is after creation
        // time window is implementation dependent
        assertThat(res).allSatisfy(x -> assertThat(x.expiration()).isAfter(x.creation()));

        // we have various types of objects and each type has 100 objects or more
        var countByExtension = res.stream().map(x -> {
            var tokens = x.uri().split("\\.");
            return tokens[tokens.length-1];
        }).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        // Will need to change when we refresh the data and tak/aspa/gbr are added
        assertThat(countByExtension.keySet()).containsExactlyInAnyOrder("asa", "cer", "mft", "roa", "crl");
        assertThat(countByExtension).allSatisfy((type, count) -> {
            if ("asa".equals(type)) {
                assertThat(count).isGreaterThan(0);
            } else {
                assertThat(count).isGreaterThan(100);
            }
        });

        // at least 50% of objects is valid for more than a month (effectively: certificates)
        assertThat(res).filteredOn(x -> x.expiration().isAfter(Instant.now().plus(Duration.ofDays(30))))
                .hasSizeGreaterThan(objects.size() / 2)
                .hasSizeLessThan(objects.size());
    }
}
