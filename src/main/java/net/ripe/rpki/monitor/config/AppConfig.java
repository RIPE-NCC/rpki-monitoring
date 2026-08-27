package net.ripe.rpki.monitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Getter
@Setter
public class AppConfig {

    private MonitorProperties properties;
    private RrdpConfig rrdpConfig;
    private RsyncConfig rsyncConfig;
    private CoreConfig coreConfig;
    private ApplicationInfo info;

    private ObjectFilterConfig objectFilterConfig;

    public AppConfig(MonitorProperties properties,
                     RrdpConfig rrdpConfig,
                     RsyncConfig rsyncConfig,
                     CoreConfig coreConfig,
                     ApplicationInfo info,
                     ObjectFilterConfig objectFilterConfig) {
        this.properties = properties;
        this.rrdpConfig = rrdpConfig;
        this.rsyncConfig = rsyncConfig;
        this.coreConfig = coreConfig;
        this.info = info;
        this.objectFilterConfig = objectFilterConfig;
    }

    @Bean
    public static ApplicationInfo appInfo(
            Optional<GitProperties> gitProperties
    ) {
        return new ApplicationInfo(gitProperties.map(GitProperties::getShortCommitId).orElse("unknown"));
    }
}
