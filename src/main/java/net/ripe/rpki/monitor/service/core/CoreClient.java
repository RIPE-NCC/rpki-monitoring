package net.ripe.rpki.monitor.service.core;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.monitor.MonitorProperties;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Setter
@Service
public class CoreClient {
    @Getter
    private final String name = "core";
    private final String url;
    private final RestTemplate restTemplate;
    private final CollectorUpdateMetrics collectorUpdateMetrics;

    @Autowired
    public CoreClient(@Value("${core.url}") String url,
                      @Value("${core.api-key}") String apikey,
                      RestTemplateBuilder builder,
                      MonitorProperties properties,
                      CollectorUpdateMetrics collectorUpdateMetrics) {
        this.collectorUpdateMetrics = collectorUpdateMetrics;
        this.url = url;
        this.restTemplate = builder
                .defaultHeader("user-agent", String.format("rpki-monitor %s", properties.getVersion()))
                .defaultHeader(properties.getInternalApiKeyHeader(), apikey)
                .rootUri(url)
                .build();

    }

    public List<PublishedObjectEntry> publishedObjects() {
        try {
            final var res = restTemplate.getForObject("/api/published-objects", PublishedObjectEntry[].class);
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), name, "published-objects").objectCount(res.length, 0, 0);
            return Arrays.asList(res);
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), name, "published-objects").zeroCounters();
            throw e;
        }
    }
}
