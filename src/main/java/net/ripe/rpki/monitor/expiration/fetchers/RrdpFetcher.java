package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Strings;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.config.RrdpConfig;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import net.ripe.rpki.monitor.util.http.ConnectToAddressResolverGroup;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import reactor.netty.http.client.HttpClient;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Verify.verifyNotNull;

@Slf4j
@Getter
public class RrdpFetcher implements RepoFetcher {

    private final RrdpConfig.RrdpRepositoryConfig config;

    private final WebClient httpClient;
    private final FetcherMetrics.RRDPFetcherMetrics metrics;

    private String lastSnapshotUrl;

    public RrdpFetcher(
            RrdpConfig.RrdpRepositoryConfig config,
            AppConfig appConfig,
            FetcherMetrics fetcherMetrics,
            WebClient.Builder webclientBuilder) {
        this.config = config;
        this.httpClient = configureWebclient(webclientBuilder, config.getConnectTo(), "rpki-monitor %s".formatted(appConfig.getInfo().gitCommitId()));

        this.metrics = fetcherMetrics.rrdp(
                Strings.isNullOrEmpty(config.getOverrideHostname()) ?
                        config.getNotificationUrl() :
                        String.format("%s@%s", config.getNotificationUrl(), config.getOverrideHostname()));

        log.info("RrdpFetcher({}, {}, {})", config.getName(), config.getNotificationUrl(), config.getOverrideHostname());
    }

    private WebClient configureWebclient(WebClient.Builder builder, Map<String, String> connectTo, String userAgent) {
        // remember: read and write timeouts are per read, not for a request.
        return builder
                .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create()
                        .resolver(new ConnectToAddressResolverGroup(connectTo))
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .responseTimeout(Duration.ofMillis(5000))
                        .doOnConnected(conn ->
                                conn.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                                    .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                        )
                    )
                )
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    private byte[] blockForHttpGetRequest(String uri, Duration timeout) {
        return httpClient.get().uri(uri).retrieve().bodyToMono(byte[].class).block(timeout);
    }

    @Override
    public Meta meta() {
        return new Meta(config.getName(), config.getNotificationUrl());
    }

    /**
     * Load snapshot and validate hash
     */
    private byte[] loadSnapshot(String snapshotUrl, String desiredSnapshotHash) throws SnapshotStructureException {
        log.info("loading {} RRDP snapshot from {}", config.getName(), snapshotUrl);

        final byte[] snapshotBytes = blockForHttpGetRequest(snapshotUrl, config.getTotalRequestTimeout());
        verifyNotNull(snapshotBytes);

        final String realSnapshotHash = Sha256.asString(snapshotBytes);
        if (!realSnapshotHash.equalsIgnoreCase(desiredSnapshotHash)) {
            throw new SnapshotStructureException(snapshotUrl, "with len(content) = %d had sha256(content) = %s, expected %s".formatted(snapshotBytes.length, realSnapshotHash, desiredSnapshotHash));
        }

        return snapshotBytes;
    }

    @Override
    public Map<String, RpkiObject> fetchObjects() throws SnapshotStructureException, SnapshotNotModifiedException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final byte[] notificationBytes = blockForHttpGetRequest(config.getNotificationUrl(), config.getTotalRequestTimeout());
            verifyNotNull(notificationBytes);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationBytes));

            final int notificationSerial = Integer.parseInt(notificationXmlDoc.getDocumentElement().getAttribute("serial"));

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = config.overrideHostname(snapshotTag.getAttributes().getNamedItem("uri").getNodeValue());
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            verifyNotNull(snapshotUrl);
            if (snapshotUrl.equals(lastSnapshotUrl)) {
                log.info("not updating: {} snapshot url {} is the same as during the last check.", config.getName(), snapshotUrl);
                metrics.success(notificationSerial, 0);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }

            final byte[] snapshotContent = loadSnapshot(snapshotUrl, desiredSnapshotHash);

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(snapshotContent));
            var doc = snapshotXmlDoc.getDocumentElement();

            // Check attributes of root snapshot element (mostly: that serial matches)
            var querySnapshot = XPathFactory.newDefaultInstance().newXPath().compile("/snapshot");
            var snapshotNodes = (NodeList) querySnapshot.evaluate(doc, XPathConstants.NODESET);
            // It is invariant that there is only one root element in an XML file, but it could still contain a different
            // root tag => 0
            if (snapshotNodes.getLength() != 1) {
                throw new SnapshotStructureException(snapshotUrl, "No <snapshot>...</snapshot> root element found");
            } else {
                var item = snapshotNodes.item(0);
                int snapshotSerial = Integer.parseInt(item.getAttributes().getNamedItem("serial").getNodeValue());

                if (notificationSerial != snapshotSerial) {
                    throw new SnapshotStructureException(snapshotUrl, "contained serial=%d, expected=%d".formatted(snapshotSerial, notificationSerial));
                }
            }

            var processPublishElementResult = processPublishElements(doc);

            metrics.success(notificationSerial, processPublishElementResult.collisionCount);
            // We have successfully updated from the snapshot, store the URL
            lastSnapshotUrl = snapshotUrl;

            return processPublishElementResult.objects;
        } catch (SnapshotStructureException e) {
            metrics.failure();
            throw e;
        } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException | NumberFormatException e) {
            // recall: IOException, ConnectException are subtypes of IOException
            metrics.failure();
            throw new FetcherException(e);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Timeout")) {
                metrics.timeout();
            }
            throw e;
        } catch (WebClientRequestException e) {
            // TODO: Exception handling could be a lot nicer. However we are mixing reactive and synchronous code,
            //  and a nice solution probably requires major changes.
            log.error("Web client request exception, only known cause is a timeout.", e);
            metrics.timeout();
            throw e;
        }
    }

    private ProcessPublishElementResult processPublishElements(Element doc) throws XPathExpressionException {
        var queryPublish = XPathFactory.newDefaultInstance().newXPath().compile("/snapshot/publish");
        final NodeList publishedObjects = (NodeList) queryPublish.evaluate(doc, XPathConstants.NODESET);

        var collisionCount = new AtomicInteger();

        var decoder = Base64.getDecoder();

        var objects = IntStream
                .range(0, publishedObjects.getLength())
                .mapToObj(publishedObjects::item)
                .map(item -> {
                    var objectUri = item.getAttributes().getNamedItem("uri").getNodeValue();
                    var content = item.getTextContent();

                    try {
                        // Surrounding whitespace is allowed by xsd:base64Binary. Trim that
                        // off before decoding. See also:
                        // https://www.w3.org/TR/2004/PER-xmlschema-2-20040318/datatypes.html#base64Binary
                        var decoded = decoder.decode(content.trim());
                        return ImmutablePair.of(objectUri, new RpkiObject(decoded));
                    } catch (RuntimeException e) {
                        log.error("cannot decode object data for URI {}\n{}", objectUri, content);
                        throw e;
                    }
                })
                // group by url to detect duplicate urls: keeps the first element, will cause a diff between
                // the sources being monitored.
                .collect(Collectors.groupingBy(Pair::getLeft))
                // invariant: every group has at least 1 item
                .entrySet().stream()
                .map(item -> {
                    if (item.getValue().size() > 1) {
                        log.warn("Multiple objects for {}, keeping first element: {}", item.getKey(), item.getValue().stream().map(coll -> Sha256.asString(coll.getRight().getBytes())).collect(Collectors.joining(", ")));
                        collisionCount.addAndGet(item.getValue().size() - 1);
                        return item.getValue().get(0);
                    }
                    return item.getValue().get(0);
                })
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        return new ProcessPublishElementResult(objects, collisionCount.get());
    }

    record ProcessPublishElementResult(Map<String, RpkiObject> objects, int collisionCount) {};
}
