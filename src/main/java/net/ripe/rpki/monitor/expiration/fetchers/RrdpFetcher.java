package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.hash.Hashing;
import io.micrometer.core.instrument.Gauge;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
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

@Component("RrdpFetcher")
public class RrdpFetcher implements RepoFetcher {
    private final RestTemplate restTemplate;
    private final String notificationXmlUrl;

    @Autowired
    public RrdpFetcher(@Value("${rrdp.url}")final String rrdpUrl, @Qualifier("rrdp-resttemplate") final RestTemplate restTemplate) {
        this.notificationXmlUrl = String.format("%s/notification.xml", rrdpUrl);
        this.restTemplate = restTemplate;
    }

    public Map<String, byte[]> fetchObjects() throws FetcherException {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final String notificationXml = restTemplate.getForObject(notificationXmlUrl, String.class);
            final Document notificationXmlDoc =  documentBuilder.parse(new ByteArrayInputStream(notificationXml.getBytes()));

            final String snapshotUrl = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0).getAttributes().getNamedItem("uri").getNodeValue();
            final String snapshotXml = restTemplate.getForObject(snapshotUrl, String.class);
            assert snapshotXml != null;
            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(snapshotXml.getBytes()));

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
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new FetcherException(e);
        }
    }
}
