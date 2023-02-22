package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.config.RrdpConfig;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Http;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Verify.verifyNotNull;

@Slf4j
@Getter
public class RrdpFetcher implements RepoFetcher {

    private final RrdpConfig.RrdpRepositoryConfig config;
    private final Http http;
    private final FetcherMetrics.RRDPFetcherMetrics metrics;

    private String lastSnapshotUrl;

    public RrdpFetcher(RrdpConfig.RrdpRepositoryConfig config, AppConfig appConfig, FetcherMetrics fetcherMetrics) {
        this.config = config;
        this.http = new Http(String.format("rpki-monitor %s", appConfig.getInfo().gitCommitId()), config.getConnectTo());
        this.metrics = fetcherMetrics.rrdp(
                Strings.isNullOrEmpty(config.getOverrideHostname()) ?
                        config.getNotificationUrl() :
                        String.format("%s@%s", config.getNotificationUrl(), config.getOverrideHostname()));

        log.info("RrdpFetcher({}, {}, {})", config.getName(), config.getNotificationUrl(), config.getOverrideHostname());
    }

    @Override
    public Meta meta() {
        return new Meta(config.getName(), config.getNotificationUrl());
    }

    @Override
    public Map<String, RpkiObject> fetchObjects() throws SnapshotStructureException, SnapshotNotModifiedException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = http.fetch(config.getNotificationUrl());
            verifyNotNull(notificationXml);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final int notificationSerial = Integer.parseInt(notificationXmlDoc.getDocumentElement().getAttribute("serial"));

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = config.overrideHostname(snapshotTag.getAttributes().getNamedItem("uri").getNodeValue());
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            verifyNotNull(snapshotUrl);
            if (snapshotUrl.equals(lastSnapshotUrl)) {
                log.debug("not updating: {} snapshot url {} is the same as during the last check.", config.getName(), snapshotUrl);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }
            lastSnapshotUrl = snapshotUrl;

            log.info("loading {} RRDP snapshot from {}", config.getName(), snapshotUrl);

            final String snapshotXml = http.fetch(snapshotUrl);
            assert snapshotXml != null;
            final byte[] bytes = snapshotXml.getBytes();

            final String realSnapshotHash = Sha256.asString(bytes);
            if (!realSnapshotHash.equalsIgnoreCase(desiredSnapshotHash)) {
                throw new SnapshotStructureException(snapshotUrl, "with len(content) = %d had sha256(content) = %s, expected %s".formatted(bytes.length, realSnapshotHash, desiredSnapshotHash));
            }

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(bytes));
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
                var snapshotSerial = Integer.valueOf(item.getAttributes().getNamedItem("serial").getNodeValue());

                if (notificationSerial != snapshotSerial) {
                    throw new SnapshotStructureException(snapshotUrl, "contained serial=%d, expected=%d".formatted(snapshotSerial, notificationSerial));
                }
            }

            var queryPublish = XPathFactory.newDefaultInstance().newXPath().compile("/snapshot/publish");
            final NodeList publishedObjects = (NodeList) queryPublish.evaluate(doc, XPathConstants.NODESET);

            var decoder = Base64.getDecoder();
            var collisionCount = new AtomicInteger();

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
                    .map((item) -> {
                        if (item.getValue().size() > 1) {
                            log.warn("Multiple objects for {}, keeping first element: {}", item.getKey(), item.getValue().stream().map(coll -> Sha256.asString(coll.getRight().getBytes())).collect(Collectors.joining(", ")));
                            collisionCount.addAndGet(item.getValue().size() - 1);
                            return item.getValue().get(0);
                        }
                        return item.getValue().get(0);
                    })
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

            metrics.success(notificationSerial, collisionCount.get());
            return objects;
        } catch (SnapshotStructureException e) {
            metrics.failure();
            throw e;
        } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException | NumberFormatException e) {
            metrics.failure();
            throw new FetcherException(e);
        }
    }
}
