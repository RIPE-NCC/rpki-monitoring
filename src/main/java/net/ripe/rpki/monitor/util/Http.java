package net.ripe.rpki.monitor.util;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * HTTP client wrapper that fetches data.
 * <p>
 * Using the `connectTo` map requests can be sent to a different host. The origin host is used for TLS (SNI and
 * certificate validation) and in the Host header.
 */
public class Http {
    private final String userAgent;
    private final HttpClientConnectionManager connectionManager;

    public Http(String userAgent, Map<String, String> connectTo) {
        this.userAgent = userAgent;
        this.connectionManager = createConnectionManager(connectTo);
    }

    public String fetch(String url) throws IOException {
        var requestConfig = RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.ofSeconds(60))
                .setResponseTimeout(Timeout.ofSeconds(10))
                .setContentCompressionEnabled(true)
                .setRedirectsEnabled(false)
                .build();
        try (var client = createHttpClient()) {
            var req = new HttpGet(url);
            req.setConfig(requestConfig);
            req.addHeader("User-Agent", userAgent);
            return client.execute(req, new BasicHttpClientResponseHandler());
        }
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .build();
    }

    private static PoolingHttpClientConnectionManager createConnectionManager(Map<String, String> connectTo) {
        var resolver = new ConnectToResolver(connectTo);
        var socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry,
                PoolConcurrencyPolicy.STRICT,
                PoolReusePolicy.LIFO,
                TimeValue.ofSeconds(60),
                null,
                resolver,
                null
        );
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(3))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        return connectionManager;
    }
}

/**
 * DNS resolver that substitutes hosts when specified by the `connectTo` attribute.
 *
 * When a host has an entry in `connectTo`, the mapped host is resolved instead. I.e. given an entry `HOST1: HOST2`
 * in `connectTo` will resolve the address of `HOST2` when attempting to resolve `HOST1`.
 */
class ConnectToResolver extends SystemDefaultDnsResolver {
    private final Map<String, String> connectTo;

    public ConnectToResolver(Map<String, String> connectTo) {
        this.connectTo = Map.copyOf(connectTo);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return super.resolve(connectTo.getOrDefault(host, host));
    }
}
