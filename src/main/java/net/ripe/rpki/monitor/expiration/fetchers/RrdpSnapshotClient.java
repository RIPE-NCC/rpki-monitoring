package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.math.BigInteger;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
@Slf4j
public class RrdpSnapshotClient {
    private final RrdpHttpStrategy httpClient;

    /**
     * Load snapshot and validate hash
     */
    byte[] loadSnapshot(String snapshotUrl, String desiredSnapshotHash) throws RRDPStructureException, RrdpHttpStrategy.HttpResponseException, RrdpHttpStrategy.HttpTimeout {
        log.info("loading RRDP snapshot from notification file at {}", snapshotUrl);

        final byte[] snapshotBytes = httpClient.fetch(snapshotUrl);
        Verify.verifyNotNull(snapshotBytes);

        final String realSnapshotHash = Sha256.asString(snapshotBytes);
        if (!realSnapshotHash.equalsIgnoreCase(desiredSnapshotHash)) {
            throw new RRDPStructureException(snapshotUrl, "with len(content) = %d had sha256(content) = %s, expected %s".formatted(snapshotBytes.length, realSnapshotHash, desiredSnapshotHash));
        }

        return snapshotBytes;
    }

    public RrdpSnapshotState fetchObjects(String notificationUrl, Optional<RrdpSnapshotState> previousState) throws RRDPStructureException, SnapshotNotModifiedException, RepoUpdateAbortedException, RrdpHttpStrategy.HttpResponseException, RrdpHttpStrategy.HttpTimeout {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final byte[] notificationBytes = httpClient.fetch(notificationUrl);
            Verify.verifyNotNull(notificationBytes);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationBytes));

            final BigInteger notificationSerial = new BigInteger(notificationXmlDoc.getDocumentElement().getAttribute("serial"));
            final String sessionId = notificationXmlDoc.getDocumentElement().getAttribute("session_id");
            try {
                var sessionUUID = UUID.fromString(sessionId); // throws IllegalArgumentException if not a valid UUID
                if (sessionUUID.version() != 4) {
                    throw new RRDPStructureException(notificationUrl, "session_id %s is not a valid UUIDv4 (version: %d)".formatted(sessionId, sessionUUID.version()));
                }
            } catch (IllegalArgumentException e) {
                throw new RRDPStructureException(notificationUrl, "session_id %s is not a valid UUID".formatted(sessionId));
            }

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = httpClient.overrideHostname(snapshotTag.getAttributes().getNamedItem("uri").getNodeValue());
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            Verify.verifyNotNull(snapshotUrl);
            if (previousState.map(state -> state.snapshotUrl.equals(snapshotUrl)).orElse(false)) {
                log.info("not updating: snapshot url {} is the same as during the last check.", snapshotUrl);
                throw new SnapshotNotModifiedException(snapshotUrl);
            }

            final byte[] snapshotContent = loadSnapshot(snapshotUrl, desiredSnapshotHash);

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(snapshotContent));
            var doc = snapshotXmlDoc.getDocumentElement();

            validateSnapshotStructure(notificationSerial, snapshotUrl, doc);

            var processPublishElementResult = processPublishElements(doc);

            return new RrdpSnapshotState(
                    snapshotUrl,
                    sessionId,
                    notificationSerial,
                    processPublishElementResult.objects,
                    processPublishElementResult.collisionCount
            );

        } catch (RRDPStructureException e) {
            throw e;
        } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException |
                 NumberFormatException e) {
            // recall: IOException, ConnectException are subtypes of IOException
            throw new FetcherException(e);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Timeout")) {
                log.info("Timeout while loading RRDP repo: details={} url={}", httpClient, notificationUrl);
                throw new RepoUpdateAbortedException(notificationUrl, httpClient, e);
            } else {
                throw e;
            }
        }
    }

    static void validateSnapshotStructure(BigInteger notificationSerial, String snapshotUrl, Element doc) throws XPathExpressionException, RRDPStructureException {
        // Check attributes of root snapshot element (mostly: that serial matches)
        var querySnapshot = XPathFactory.newDefaultInstance().newXPath().compile("/snapshot");
        var snapshotNodes = (NodeList) querySnapshot.evaluate(doc, XPathConstants.NODESET);
        // It is invariant that there is only one root element in an XML file, but it could still contain a different
        // root tag => 0
        if (snapshotNodes.getLength() != 1) {
            throw new RRDPStructureException(snapshotUrl, "No <snapshot>...</snapshot> root element found");
        } else {
            var item = snapshotNodes.item(0);
            var snapshotSerial = new BigInteger(item.getAttributes().getNamedItem("serial").getNodeValue());

            if (!notificationSerial.equals(snapshotSerial)) {
                throw new RRDPStructureException(snapshotUrl, "contained serial=%d, expected=%d".formatted(snapshotSerial, notificationSerial));
            }
        }
    }

    ProcessPublishElementResult processPublishElements(Element doc) throws XPathExpressionException {
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
                .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));

        return new ProcessPublishElementResult(objects, collisionCount.get());
    }

    record ProcessPublishElementResult(ImmutableMap<String, RpkiObject> objects, int collisionCount) {}

    record RrdpSnapshotState(String snapshotUrl, String sessionId, BigInteger serial, ImmutableMap<String, RpkiObject> objects, int collisionCount){
        public long serialAsLong() {
            return serial.mod(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        }

    }
}