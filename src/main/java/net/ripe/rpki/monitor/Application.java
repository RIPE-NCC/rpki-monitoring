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
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Bean(name = "rrdp-resttemplate")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .defaultHeader("user-agent", String.format("rpki-monitor %s", properties.getVersion()))
            .build();
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
        repos.add(Triple.of("rrdp", config.getRrdpConfig().getMainUrl(), RepositoryTracker.Type.RRDP));
        repos.addAll(toTriplets(config.getRrdpConfig().getOtherUrls(), RepositoryTracker.Type.RRDP));
        repos.add(Triple.of("rsync", config.getRsyncConfig().getMainUrl(), RepositoryTracker.Type.RSYNC));
        repos.addAll(toTriplets(config.getRsyncConfig().getOtherUrls(), RepositoryTracker.Type.RSYNC));

        var state = RepositoriesState.init(repos);
        state.addHook(tracker -> publishedObjectsSummary.updateSize(Instant.now(), tracker));
        state.addHook(tracker -> {
            var now = Instant.now();
            var others = state.otherTrackers(tracker);
            publishedObjectsSummary.updateAndGetPublishedObjectsDiff(now, tracker, others);
        });
        state.addHook(tracker -> {
            var now = Instant.now();
            objectExpirationMetrics.trackExpiration(tracker.getUrl(), now, tracker.entriesAt(now));
        });
        state.addHook(tracker ->
            log.info("Updated {} repository at {}; it now has {} entries.", tracker.getType(), tracker.getUrl(), tracker.size(Instant.now()))
        );
        return state;
    }

    private void checkOverlappingRepositoryKeys(AppConfig config) {
        var builtinKeys = List.of("core", "rrdp", "rsync");
        var rrdpKeys = config.getRrdpConfig().getOtherUrls().keySet();
        var rsyncKeys = config.getRsyncConfig().getOtherUrls().keySet();
        Validate.isTrue(
                rrdpKeys.stream().noneMatch(builtinKeys::contains),
                "RRDP other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                rsyncKeys.stream().noneMatch(builtinKeys::contains),
                "Rsync other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                rrdpKeys.stream().noneMatch(rsyncKeys::contains),
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
