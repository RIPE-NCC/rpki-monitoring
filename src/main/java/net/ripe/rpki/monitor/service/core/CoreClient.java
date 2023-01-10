package net.ripe.rpki.monitor.service.core;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.config.CoreConfig;
import net.ripe.rpki.monitor.config.MonitorProperties;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Setter
@Service
public class CoreClient {
    @Getter
    private final String name = "core";
    private final String url;
    private final RestTemplate restTemplate;
    private final CollectorUpdateMetrics collectorUpdateMetrics;

    @Autowired
    public CoreClient(CoreConfig coreConfig,
                      RestTemplateBuilder builder,
                      AppConfig appConfig,
                      CollectorUpdateMetrics collectorUpdateMetrics) {
        this.collectorUpdateMetrics = collectorUpdateMetrics;
        this.url = coreConfig.getUrl();
        this.restTemplate = builder
                .defaultHeader("user-agent", String.format("rpki-monitor %s", appConfig.getInfo().gitCommitId()))
                .defaultHeader(appConfig.getProperties().getInternalApiKeyHeader(), coreConfig.getApiKey())
                .rootUri(url)
                .build();

    }

    public List<PublishedObjectEntry> publishedObjects() {
        try {
            var res = Optional.ofNullable(
                restTemplate.getForObject("/api/published-objects", PublishedObjectEntry[].class)
            ).orElse(new PublishedObjectEntry[0]);
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), name, "published-objects").objectCount(res.length, 0, 0);
            return Arrays.asList(res);
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), name, "published-objects").zeroCounters();
            throw e;
        }
    }
}
