package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component("RrdpFetcher")
public class RrdpFetcher implements RepoFetcher {
    private final RestTemplate restTemplate;
    private final String notificationXmlUrl;

    private String lastSnapshotUrl;

    @Autowired
    public RrdpFetcher(
        @Value("${rrdp.url}") final String rrdpUrl,
        @Qualifier("rrdp-resttemplate") final RestTemplate restTemplate
    ) {
        this.notificationXmlUrl = String.format("%s/notification.xml", rrdpUrl);
        this.restTemplate = restTemplate;
    }

    public Map<String, byte[]> fetchObjects() throws FetcherException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = restTemplate.getForObject(notificationXmlUrl, String.class);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = snapshotTag.getAttributes().getNamedItem("uri").getNodeValue();
            final String snapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            assert snapshotUrl != null;
            if (snapshotUrl.equals(lastSnapshotUrl)) {
                log.debug("not updating: snapshot url {} is the same as during the last check.", snapshotUrl);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }
            lastSnapshotUrl = snapshotUrl;

            log.info("loading rrdp snapshot from {}", snapshotUrl, notificationXmlUrl);

            final String snapshotXml = restTemplate.getForObject(snapshotUrl, String.class);
            assert snapshotXml != null;
            final byte[] bytes = snapshotXml.getBytes();

            final String realHash = Hashing.sha256().hashBytes(bytes).toString();
            if (!realHash.toLowerCase().equals(snapshotHash.toLowerCase())) {
                throw new SnapshotWrongHashException(snapshotHash, realHash);
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

                    return ImmutablePair.of(objectUri, decoded);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        } catch (Exception e) {
            throw new FetcherException(e);
        }
    }
}
