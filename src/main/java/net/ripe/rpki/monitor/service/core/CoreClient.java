package net.ripe.rpki.monitor.service.core;

import lombok.Data;
import lombok.Setter;
import net.ripe.rpki.monitor.MonitorProperties;
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
    private MonitorProperties config;

    @Qualifier("rpki-core-resttemplate")
    @Autowired
    private RestTemplate restTemplate;

    public List<PublishedObjectEntry> publishedObjects() {
        final var res = restTemplate.getForObject("/api/published-objects", PublishedObjectEntry[].class);
        return Arrays.asList(res);
    }
}
