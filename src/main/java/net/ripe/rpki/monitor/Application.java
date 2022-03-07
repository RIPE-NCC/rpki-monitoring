package net.ripe.rpki.monitor;

import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@ConfigurationPropertiesScan("net.ripe.rpki.monitor")
@SpringBootApplication
public class Application {
    @Autowired
    private MonitorProperties properties;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RepositoriesState repositoriesState(
            final AppConfig config,
            final PublishedObjectsSummaryService publishedObjectsSummary,
            ObjectExpirationMetrics objectExpirationMetrics
    ) {
        checkOverlappingRepositoryKeys(config);
        var repos = new ArrayList<Triple<String, String, RepositoryTracker.Type>>();
        repos.add(Triple.of("core", config.getCoreUrl(), RepositoryTracker.Type.CORE));
        repos.addAll(
                config.getRrdpConfig().getTargets().stream()
                        .map(repo -> Triple.of(repo.getName(), repo.getNotificationUrl(), RepositoryTracker.Type.RRDP))
                        .collect(Collectors.toSet())
        );
        repos.add(Triple.of("rsync", config.getRsyncConfig().getMainUrl(), RepositoryTracker.Type.RSYNC));
        repos.addAll(toTriplets(config.getRsyncConfig().getOtherUrls(), RepositoryTracker.Type.RSYNC));

        var state = RepositoriesState.init(repos, publishedObjectsSummary.maxThreshold());
        state.addHook(tracker -> publishedObjectsSummary.updateSize(Instant.now(), tracker));
        state.addHook(tracker -> {
            var now = Instant.now();
            var others = state.otherTrackers(tracker);
            publishedObjectsSummary.updateAndGetPublishedObjectsDiff(now, tracker, others);
        });
        state.addHook(tracker -> {
            if (tracker.getType() == RepositoryTracker.Type.RRDP || tracker.getType() == RepositoryTracker.Type.RSYNC) {
                var now = Instant.now();
                objectExpirationMetrics.trackExpiration(tracker.getUrl(), now, tracker.view(now).entries());
            }
        });
        state.addHook(tracker -> log.info(
            "Updated {} repository {} at {}; it now has {} entries.",
            tracker.getType(),
            tracker.getTag(),
            tracker.getUrl(),
            tracker.view(Instant.now()).size()
        ));
        return state;
    }

    /**
     * Tags need to be unique, both within, and between sources.
     */
    private void checkOverlappingRepositoryKeys(AppConfig config) {
        var builtinKeys = Set.of("core", "rsync");

        var rrdpKeys = config.getRrdpConfig().getTargets().stream().map(RrdpConfig.RrdpRepositoryConfig::getName).collect(Collectors.toSet());
        var rsyncKeys = config.getRsyncConfig().getOtherUrls().keySet();

        // Check for overlap within-source
        Validate.isTrue(
                rrdpKeys.size() == config.getRrdpConfig().getTargets().size(),
                "There are duplicate keys in the `name` of the rrdp targets."
        );
        Validate.isTrue(
                rsyncKeys.size() == config.getRsyncConfig().getOtherUrls().size(),
                "There are duplicate keys in the rsync targets."
        );

        // Check for overlap between sources
        Validate.isTrue(
                Collections.disjoint(builtinKeys, rrdpKeys),
                "RRDP other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                Collections.disjoint(builtinKeys, rsyncKeys),
                "Rsync other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                Collections.disjoint(rrdpKeys, rsyncKeys),
                "RRDP and rsync other-urls keys overlap"
        );
    }

    private <K, V, Z> List<Triple<K, V, Z>> toTriplets(Map<K, V> m, Z z) {
        return m.entrySet().stream()
                .map(x -> Triple.of(x.getKey(), x.getValue(), z))
                .collect(Collectors.toList());
    }

    @Bean
    public InfoContributor versionInfoContributor(final MonitorProperties config) {
        return builder -> {
            builder.withDetail("version", config.getVersion());
        };
    }

    /**
     * Drop all the http.client.requests metrics to prevent metrics explosion
     */
    @Bean
    public MeterFilter dropHttpClientRequestMetrics() {
        return MeterFilter.denyNameStartsWith("http.client.requests");
    }
}
