package net.ripe.rpki.monitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    @Autowired
    private ApplicationInfo info;

    @Autowired
    private ObjectFilterConfig objectFilterConfig;

    @Bean
    public static ApplicationInfo appInfo(
            Optional<GitProperties> gitProperties
    ) {
        return new ApplicationInfo(gitProperties.map(GitProperties::getShortCommitId).orElse("unknown"));
    }

    public static boolean ignoreObject(AppConfig appConfig, String uri, String hash) {
        if (appConfig == null) {
            return false;
        }
        if (appConfig.getObjectFilterConfig() == null) {
            return false;
        }
        return appConfig.getObjectFilterConfig().ignore(uri, hash);
    }
}
