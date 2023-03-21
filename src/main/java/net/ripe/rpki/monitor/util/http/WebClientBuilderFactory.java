package net.ripe.rpki.monitor.util.http;

import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Customise a spring-configured WebClient.Builder with rpki-monitoring specific settings.
 *
 * 2023-3-10: Right now this is my most inspired way to customise the WebClient builder the two ways
 *            (with and without custom DNS library) this project requires.
 *
 * @param eventLoopGroup to run in
 * @param baseBuilder    to customise
 * @param userAgent      to set in headers
 */
public record WebClientBuilderFactory(EventLoopGroup eventLoopGroup, WebClient.Builder baseBuilder, String userAgent) {

    /**
     * Configure a HttpClient without setting the resolver
     */
    private HttpClient initialHttpClientConfig() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(5000))
                // remember: read and write timeouts are per read, not for a request.
                .doOnConnected(conn ->
                        conn
                                // some endpoints (e.g. rpki core) are slow, 5s -> 60s.
                                .addHandlerLast(new ReadTimeoutHandler(60000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(60000, TimeUnit.MILLISECONDS))
                );
    }

    private WebClient.Builder forHttpClient(HttpClient httpClient) {
        return baseBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent);

    }

    public WebClient.Builder builder() {
        return forHttpClient(
                initialHttpClientConfig()
                        .resolver(spec ->
                            spec.resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED)
                                .completeOncePreferredResolved(false)
                        )
        );
    }

    public WebClient.Builder connectToClientBuilder(Map<String, String> connectTo) {
        final var connectToResolver = new ConnectToAddressResolverGroup(
                connectTo,
                new DnsNameResolverBuilder()
                        .completeOncePreferredResolved(false)
                        .resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED)
                        // next two are REQUIRED
                        .eventLoop(eventLoopGroup().next())
                        .channelFactory(NioDatagramChannel::new)
        );

        return forHttpClient(initialHttpClientConfig().resolver(connectToResolver));
    }
}
