package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.client.RestTemplate;
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
public class RrdpFetcher implements RepoFetcher {

    private final RestTemplate restTemplate;
    private final String notificationXmlUrl;
    private final String rrdpUrl;
    private String lastSnapshotUrl;

    public RrdpFetcher(String rrdpUrl, RestTemplate restTemplate) {
        this.rrdpUrl = rrdpUrl;
        this.notificationXmlUrl = String.format("%s/notification.xml", rrdpUrl);
        this.restTemplate = restTemplate;
    }

    @Override
    public String repositoryUrl() {
        return rrdpUrl;
    }

    public Map<String, RpkiObject> fetchObjects() throws SnapshotNotModifiedException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = restTemplate.getForObject(notificationXmlUrl, String.class);
            verifyNotNull(notificationXml);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = snapshotTag.getAttributes().getNamedItem("uri").getNodeValue();
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            verifyNotNull(snapshotUrl);
            if (snapshotUrl.equals(lastSnapshotUrl)) {
                log.debug("not updating: snapshot url {} is the same as during the last check.", snapshotUrl);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }
            lastSnapshotUrl = snapshotUrl;

            log.info("loading RRDP snapshot from {}", snapshotUrl);

            final String snapshotXml = restTemplate.getForObject(snapshotUrl, String.class);
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
}
