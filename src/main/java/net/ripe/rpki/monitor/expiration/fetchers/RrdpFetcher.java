package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.config.RrdpConfig;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.http.WebClientBuilderFactory;
import org.springframework.http.HttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Getter
public class RrdpFetcher implements RepoFetcher {

    private final RrdpConfig.RrdpRepositoryConfig config;

    private final WebClient httpClient;
    private final FetcherMetrics.RRDPFetcherMetrics metrics;
    private final RrdpSnapshotClient rrdpSnapshotClient;

    private Optional<RrdpSnapshotClient.RrdpSnapshotState> lastUpdate = Optional.empty();

    public RrdpFetcher(
            RrdpConfig.RrdpRepositoryConfig config,
            FetcherMetrics fetcherMetrics,
            WebClientBuilderFactory webclientBuilderFactory) {
        this.config = config;
        this.httpClient = webclientBuilderFactory.connectToClientBuilder(config.getConnectTo()).build();

        this.metrics = fetcherMetrics.rrdp(config);
        this.rrdpSnapshotClient = new RrdpSnapshotClient(new WebclientRrdpHttpStrategy(config));

        log.info("RrdpFetcher({}, {}, {}, {})", config.getName(), config.getNotificationUrl(), config.getOverrideHostname(), config.getConnectTo());
    }

    @Override
    public Meta meta() {
        return new Meta(config.getName(), config.getNotificationUrl());
    }

    @Override
    public ImmutableMap<String, RpkiObject> fetchObjects() throws RRDPStructureException, SnapshotNotModifiedException, RepoUpdateAbortedException, RepoUpdateFailedException {
        try {
            var update = rrdpSnapshotClient.fetchObjects(config.getNotificationUrl(), lastUpdate);
            metrics.success(update.serialAsLong(), update.collisionCount());

            this.lastUpdate = Optional.of(update);

            return update.objects();
        } catch (SnapshotNotModifiedException e) {
            lastUpdate.ifPresent(update -> metrics.success(update.serialAsLong(), update.collisionCount()));
            throw e;
        } catch (RRDPStructureException | FetcherException e) {
            metrics.failure();
            throw e;
        } catch (RrdpHttp.HttpResponseException e) {
            log.error("[{}] HTTP error on {} {}: {}", config.getName(), e.getMethod(), e.getUri(), e.getStatusCode());
            metrics.failure();
            throw new RepoUpdateFailedException(e.getUri(), e.getClient(), e);
        } catch (RrdpHttp.HttpTimeout e) {
            log.info("[{}] HTTP timeout on {} {}", config.getName(), e.getMethod(), e.getUri());
            metrics.timeout();
            throw new RepoUpdateAbortedException(e.getUri(), e.getClient(), e);
        }
    }

    private class WebclientRrdpHttpStrategy implements RrdpHttp {
        private final RrdpConfig.RrdpRepositoryConfig config;

        public WebclientRrdpHttpStrategy(RrdpConfig.RrdpRepositoryConfig config) {
            this.config = config;
        }

        @Override
        public byte[] fetch(String uri) throws HttpResponseException, HttpTimeout {
            try {
                return httpClient.get().uri(uri)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(config.getTotalRequestTimeout());
            } catch (WebClientResponseException e) {
                var maybeRequest = Optional.ofNullable(e.getRequest());

                // Can be either a HTTP non-2xx or a timeout
                log.error("Webclient error for {} {}: Can be HTTP non-200 or a timeout. For 2xx responses, assume it's a timeout reading the response.", maybeRequest.map(HttpRequest::getMethod), maybeRequest.map(HttpRequest::getURI), e);
                if (e.getStatusCode().is2xxSuccessful()) {
                    throw new HttpTimeout(this, maybeRequest.map(HttpRequest::getMethod).orElse(null), uri, e);
                } else {
                    throw new HttpResponseException(this, maybeRequest.map(HttpRequest::getMethod).orElse(null), uri, e.getStatusCode(), e);
                }
            } catch (WebClientRequestException e) {
                // Only known cause is a timeout
                throw new HttpTimeout(this, uri, e);
            }
        }

        @Override
        public String transformHostname(String url) {
            return config.overrideHostname(url);
        }

        @Override
        public String describe() {
            return (config.getOverrideHostname() != null ? "override-host-name=" + config.getOverrideHostname() + " ": "") + "connect-to=" + config.getConnectTo().toString();
        }
    }
}
