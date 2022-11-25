package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.MonitorProperties;
import net.ripe.rpki.monitor.RrdpConfig;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
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

    public RrdpFetcher(RrdpConfig.RrdpRepositoryConfig config, MonitorProperties properties, FetcherMetrics fetcherMetrics) {
        this.config = config;
        this.http = new Http(String.format("rpki-monitor %s", properties.getVersion()), config.getConnectTo());
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
    public Map<String, RpkiObject> fetchObjects() throws SnapshotNotModifiedException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = http.fetch(config.getNotificationUrl());
            verifyNotNull(notificationXml);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final int serial = Integer.parseInt(notificationXmlDoc.getDocumentElement().getAttribute("serial"));

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
                throw new SnapshotWrongHashException(desiredSnapshotHash, realSnapshotHash);
            }

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(bytes));
            final NodeList publishedObjects = snapshotXmlDoc.getDocumentElement().getElementsByTagName("publish");

            var decoder = Base64.getDecoder();
            var objects = IntStream
                .range(0, publishedObjects.getLength())
                .mapToObj(publishedObjects::item)
                .map(item -> {
                    var objectUri = item.getAttributes().getNamedItem("uri").getNodeValue();
                    var content = item.getTextContent();

                    try {
                        var decoded = decoder.decode(content.trim());
                        return ImmutablePair.of(objectUri, new RpkiObject(decoded));
                    } catch (RuntimeException e) {
                        log.error("cannot decode object data for URI {}\n{}", objectUri, content);
                        throw e;
                    }
                })
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

            metrics.success(serial);
            return objects;
        } catch (ParserConfigurationException | SAXException | IOException | NumberFormatException | SnapshotWrongHashException e) {
            metrics.failure();
            throw new FetcherException(e);
        }
    }
}
