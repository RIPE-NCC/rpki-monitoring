package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.MonitorProperties;
import net.ripe.rpki.monitor.RrdpConfig;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Verify.verifyNotNull;

@Slf4j
@Getter
public class RrdpFetcher implements RepoFetcher {

    private final MonitorProperties properties;
    private final RrdpConfig.RrdpRepositoryConfig config;
    private final PoolingHttpClientConnectionManager connectionManager;

    private String lastSnapshotUrl;

    public RrdpFetcher(
            RrdpConfig.RrdpRepositoryConfig config,
            MonitorProperties properties
    ) {
        this.properties = properties;
        this.config = config;
        this.connectionManager = createConnectionManager(config);

        log.info("RrdpFetcher({}, {}, {})", config.getName(), config.getNotificationUrl(), config.getOverrideHostname());
    }

    @Override
    public String repositoryUrl() {
        return config.getNotificationUrl();
    }

    public Map<String, RpkiObject> fetchObjects() throws SnapshotNotModifiedException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = fetch(config.getNotificationUrl());
            verifyNotNull(notificationXml);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = config.overrideHostname(snapshotTag.getAttributes().getNamedItem("uri").getNodeValue());
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            verifyNotNull(snapshotUrl);
            if (snapshotUrl.equals(lastSnapshotUrl)) {
                log.debug("not updating: snapshot url {} is the same as during the last check.", snapshotUrl);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }
            lastSnapshotUrl = snapshotUrl;

            log.info("loading RRDP snapshot from {}", snapshotUrl);

            final String snapshotXml = fetch(snapshotUrl);
            assert snapshotXml != null;
            final byte[] bytes = snapshotXml.getBytes();

            final String realSnapshotHash = Sha256.asString(bytes);
            if (!realSnapshotHash.equalsIgnoreCase(desiredSnapshotHash)) {
                throw new SnapshotWrongHashException(desiredSnapshotHash, realSnapshotHash);
            }

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(bytes));
            final NodeList publishedObjects = snapshotXmlDoc.getDocumentElement().getElementsByTagName("publish");

            return IntStream
                .range(0, publishedObjects.getLength())
                .mapToObj(publishedObjects::item)
                .map(item -> {
                    final String objectUri = item.getAttributes().getNamedItem("uri").getNodeValue();

                    final Base64.Decoder decoder = Base64.getDecoder();
                    final byte[] decoded = decoder.decode(item.getTextContent());

                    return ImmutablePair.of(objectUri, new RpkiObject(decoded));
                })
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        } catch (ParserConfigurationException | SAXException | IOException | SnapshotWrongHashException e) {
            throw new FetcherException(e);
        }
    }

    private String fetch(String url) throws IOException {
        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setRedirectsEnabled(false)
                .build();
        try (var client = httpClient(connectionManager)) {
            var req = new HttpGet(url);
            req.setConfig(requestConfig);
            req.addHeader("User-Agent", String.format("rpki-monitor %s", properties.getVersion()));
            return client.execute(req, new BasicHttpClientResponseHandler());
        }
    }

    private static CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .build();
    }

    private static PoolingHttpClientConnectionManager createConnectionManager(RrdpConfig.RrdpRepositoryConfig config) {
        var resolver = new ConnectToResolver(config.getConnectTo());
        var socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build();
        return new PoolingHttpClientConnectionManager(
                socketFactoryRegistry,
                PoolConcurrencyPolicy.STRICT,
                PoolReusePolicy.LIFO,
                TimeValue.ofSeconds(60),
                null,
                resolver,
                null
        );
    }
    static class ConnectToResolver extends SystemDefaultDnsResolver {
        private final Map<String, String> connectTo;

        public ConnectToResolver(Map<String, String> connectTo) {
            this.connectTo = Map.copyOf(connectTo);
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return super.resolve(connectTo.getOrDefault(host, host));
        }
    }
}
