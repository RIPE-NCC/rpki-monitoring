package net.ripe.rpki.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties("rsync")
@Data
public class RsyncConfig {
    private String onPremiseUrl;
    private int timeout;
    private Duration interval;
    private List<String> awsUrl;
}
