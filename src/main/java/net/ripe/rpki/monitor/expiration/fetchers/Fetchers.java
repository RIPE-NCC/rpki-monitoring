package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Setter;
import net.ripe.rpki.monitor.AppConfig;
import org.springframework.stereotype.Component;

@Component("Fetchers")
@Setter
public class Fetchers {

    private final AppConfig config;

    public Fetchers(AppConfig config) {
        this.config = config;
    }

    public RepoFetcher getRsyncFetcher(String url) {
        return new RsyncFetcher(url, config.getRsyncTimeout());
    }

    public RepoFetcher getRrdpFetcher(String url) {
        return new RrdpFetcher(url, config.getRestTemplate());
    }
}
