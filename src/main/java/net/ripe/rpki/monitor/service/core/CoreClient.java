package net.ripe.rpki.monitor.service.core;

import lombok.Setter;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Setter
@Service
public class CoreClient {

    @Qualifier("rpki-core-resttemplate")
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CollectorUpdateMetrics collectorUpdateMetrics;

    public List<PublishedObjectEntry> publishedObjects() {
        try {
            final var res = restTemplate.getForObject("/api/published-objects", PublishedObjectEntry[].class);
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName());
            return Arrays.asList(res);
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName());
            throw e;
        }
    }
}
