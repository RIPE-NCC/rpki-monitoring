package net.ripe.rpki.monitor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Getter
@Setter
@AllArgsConstructor
public class AppConfig {
    private MonitorProperties properties;
    private RrdpConfig rrdpConfig;
    private RsyncConfig rsyncConfig;
    private CoreConfig coreConfig;
    private ApplicationInfo info;

    private ObjectFilterConfig objectFilterConfig;

    @Bean
    public static ApplicationInfo appInfo(
            Optional<GitProperties> gitProperties
    ) {
        return new ApplicationInfo(gitProperties.map(GitProperties::getShortCommitId).orElse("unknown"));
    }
}
