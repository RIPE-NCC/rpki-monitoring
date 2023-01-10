package net.ripe.rpki.monitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
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

    @Autowired
    private CoreConfig coreConfig;
}
