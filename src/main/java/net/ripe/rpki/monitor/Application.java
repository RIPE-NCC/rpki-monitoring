package net.ripe.rpki.monitor;

import io.netty.channel.nio.NioEventLoopGroup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.config.RrdpConfig;
import net.ripe.rpki.monitor.config.RsyncConfig;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import net.ripe.rpki.monitor.publishing.PublishedObjectsSummaryService;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import net.ripe.rpki.monitor.util.http.WebClientBuilderFactory;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static java.util.stream.Collectors.toSet;


@Slf4j
@ConfigurationPropertiesScan("net.ripe.rpki.monitor")
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public WebClientBuilderFactory webclientConfigurer(WebClient.Builder baseBuilder, AppConfig appConfig) {
        // Explicit event loop is required for custom DnsNameResolverBuilder
        NioEventLoopGroup group = new NioEventLoopGroup(1);

        return new WebClientBuilderFactory(group, baseBuilder, "rpki-monitor %s".formatted(appConfig.getInfo().gitCommitId()));
    }

    @Bean
    public RepositoriesState repositoriesState(
            @NonNull final AppConfig config,
            @NonNull final PublishedObjectsSummaryService publishedObjectsSummary,
            @NonNull ObjectExpirationMetrics objectExpirationMetrics
    ) {
        checkOverlappingRepositoryKeys(config);
        var repos = new ArrayList<Triple<String, String, RepositoryTracker.Type>>();

        if (config.getCoreConfig().isEnable()) {
            repos.add(Triple.of("core", config.getCoreConfig().getUrl(), RepositoryTracker.Type.CORE));
        }
        repos.addAll(
                config.getRrdpConfig().getTargets().stream()
                        .map(repo -> Triple.of(repo.getName(), repo.getNotificationUrl(), RepositoryTracker.Type.RRDP))
                        .collect(toSet())
        );
        repos.addAll(
                config.getRsyncConfig().getTargets().stream()
                         .map(target -> Triple.of(target.name(), target.url(), RepositoryTracker.Type.RSYNC))
                         .collect(toSet())
        );

        var state = RepositoriesState.init(repos, publishedObjectsSummary.maxThreshold());
        state.addHook(tracker -> publishedObjectsSummary.updateSizes(Instant.now(), tracker));
        state.addHook(tracker -> {
            var now = Instant.now();
            var others = state.otherTrackers(tracker);
            others.forEach(other -> publishedObjectsSummary.updateAndGetPublishedObjectsDiff(now, tracker, other));
        });
        state.addHook(tracker -> {
            if (tracker.getType() == RepositoryTracker.Type.RRDP || tracker.getType() == RepositoryTracker.Type.RSYNC) {
                var now = Instant.now();
                objectExpirationMetrics.trackExpiration(tracker.getUrl(), now, tracker.view(now).entries());
            }
        });
        state.addHook(tracker -> log.info(
            "Updated {} repository {} at {}; it now has {} entries.",
            tracker.getType(),
            tracker.getTag(),
            tracker.getUrl(),
            tracker.view(Instant.now()).size()
        ));
        return state;
    }

    /**
     * Tags need to be unique, both within, and between sources.
     */
    private void checkOverlappingRepositoryKeys(AppConfig config) {
        var builtinKeys = Set.of("core");

        var rrdpKeys = config.getRrdpConfig().getTargets().stream().map(RrdpConfig.RrdpRepositoryConfig::getName).collect(toSet());
        var rsyncKeys = config.getRsyncConfig().getTargets().stream().map(RsyncConfig.RsyncTarget::name).collect(toSet());

        // Check for overlap within-source
        Validate.isTrue(
                rrdpKeys.size() == config.getRrdpConfig().getTargets().size(),
                "There are duplicate keys in the `name` of the rrdp targets."
        );
        Validate.isTrue(
                rsyncKeys.size() == config.getRsyncConfig().getTargets().size(),
                "There are duplicate keys in the rsync targets."
        );

        // Check for overlap between sources
        Validate.isTrue(
                Collections.disjoint(builtinKeys, rrdpKeys),
                "RRDP other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                Collections.disjoint(builtinKeys, rsyncKeys),
                "Rsync other-urls keys overlap with builtin keys"
        );
        Validate.isTrue(
                Collections.disjoint(rrdpKeys, rsyncKeys),
                "RRDP and rsync other-urls keys overlap"
        );
    }

    /**
     * Drop all the http.client.requests metrics to prevent metrics explosion
     */
    @Bean
    public MeterFilter dropHttpClientRequestMetrics() {
        return MeterFilter.denyNameStartsWith("http.client.requests");
    }
}
