package net.ripe.rpki.monitor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("AppConfig")
@Getter
@Setter
public class AppConfig {

    @Autowired
    private MonitorProperties properties;

    @Autowired
    private RrdpConfig rrdpConfig;

    @Autowired
    private RsyncConfig rsyncConfig;

    @Value("${core.url}")
    private String coreUrl;

    @Value("${core.included}")
    private boolean coreIncluded;
}