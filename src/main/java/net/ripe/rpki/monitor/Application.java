package net.ripe.rpki.monitor;

import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Bean(name = "rpki-core-resttemplate")
    public RestTemplate coreClient(RestTemplateBuilder builder,
                                   @Value("${core.url}") String coreUrl,
                                   @Value("${core.api-key}") String coreApiKey) {
        return builder
            .defaultHeader("user-agent", String.format("rpki-monitor %s", properties.getVersion()))
            .defaultHeader(properties.getInternalApiKeyHeader(), coreApiKey)
            .rootUri(coreUrl)
            .build();
    }

    @Bean
    public RepositoriesState repositoriesState(
            final AppConfig config,
            final PublishedObjectsSummaryService publishedObjectsSummary
    ) {
        var repos = new ArrayList<Pair<String, String>>();
        repos.add(Pair.of("core", config.getCoreUrl()));
        repos.add(Pair.of("rrdp", config.getRrdpConfig().getMainUrl()));
        repos.addAll(toList(config.getRrdpConfig().getOtherUrls()));
        repos.add(Pair.of("rsync", config.getRsyncConfig().getMainUrl()));
        repos.addAll(toList(config.getRsyncConfig().getOtherUrls()));

        var state = RepositoriesState.init(repos);
        state.addHook((tracker) -> {
            var now = Instant.now();
            var others = state.otherTrackers(tracker);
            publishedObjectsSummary.updateAndGetPublishedObjectsDiff(now, tracker, others);
        });
        return state;
    }

    private <K, V> List<Pair<K, V>> toList(Map<K, V> m) {
        return m.entrySet().stream()
                .map(x -> Pair.of(x.getKey(), x.getValue()))
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
