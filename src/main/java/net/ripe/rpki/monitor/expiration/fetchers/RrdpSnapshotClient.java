package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.util.Sha256;
import net.ripe.rpki.monitor.util.XML;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
    private final RrdpHttp httpClient;

    /**
     * Load snapshot and validate hash
     */
    byte[] loadSnapshot(String snapshotUrl, String desiredSnapshotHash) throws RRDPStructureException, RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout {
        log.info("loading RRDP snapshot from {} {}", snapshotUrl, httpClient.describe());

        final byte[] snapshotBytes = httpClient.fetch(snapshotUrl);
        Verify.verifyNotNull(snapshotBytes, "expected non-null snapshot content from %s %s".formatted(snapshotUrl, httpClient.describe()));

        final String realSnapshotHash = Sha256.asString(snapshotBytes);
        if (!realSnapshotHash.equalsIgnoreCase(desiredSnapshotHash)) {
            throw new RRDPStructureException(snapshotUrl, "with len(content) = %d had sha256(content) = %s, expected=%s".formatted(snapshotBytes.length, realSnapshotHash, desiredSnapshotHash));
        } else {
            log.debug("verified snapshot hash: len(content)={} h(content)={} for {} {}", snapshotBytes.length, desiredSnapshotHash, snapshotUrl, httpClient.describe());
        }

        return snapshotBytes;
    }

    public RrdpSnapshotState fetchObjects(String notificationUrl, Optional<RrdpSnapshotState> previousState) throws RRDPStructureException, SnapshotNotModifiedException, RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout {
        try {
            final DocumentBuilder documentBuilder = XML.newDocumentBuilder();

            final byte[] notificationBytes = httpClient.fetch(notificationUrl);
            Verify.verifyNotNull(notificationBytes);
            final Document notificationXmlDoc = documentBuilder.parse(new ByteArrayInputStream(notificationBytes));

            final BigInteger notificationSerial = parseSerial(notificationUrl, notificationXmlDoc.getDocumentElement());
            var sessionIdUUID = validateSessionIdUUIDv4(notificationUrl, notificationXmlDoc.getDocumentElement());

            final Node snapshotTag = notificationXmlDoc.getDocumentElement().getElementsByTagName("snapshot").item(0);
            final String snapshotUrl = httpClient.transformHostname(snapshotTag.getAttributes().getNamedItem("uri").getNodeValue());
            final String desiredSnapshotHash = snapshotTag.getAttributes().getNamedItem("hash").getNodeValue();

            Verify.verify(!Strings.isNullOrEmpty(snapshotUrl));
            Verify.verify(!Strings.isNullOrEmpty(desiredSnapshotHash));
            if (previousState.map(state -> state.snapshotHash.equals(desiredSnapshotHash) && state.snapshotUrl.equals(snapshotUrl)).orElse(false)) {
                log.info("snapshot not modified: snapshot is the same as during the last check (url={} serial={} session={} client={})", snapshotUrl, sessionIdUUID, notificationSerial, httpClient.describe());
                throw new SnapshotNotModifiedException(snapshotUrl);
            } else {
                previousState.ifPresent(prev -> {
                    if (prev.snapshotHash.equals(desiredSnapshotHash)) {
                        log.error("RRDP inconsistency: hash is equal ({}) but url differs; current={} != prev={}", desiredSnapshotHash, snapshotUrl, prev.snapshotUrl);
                    } else if (prev.snapshotUrl.equals(snapshotUrl)) {
                        log.error("RRDP inconsistency: url is equal ({}) but hash differs; current={} != prev={}", snapshotUrl, desiredSnapshotHash, prev.snapshotHash);
                    }
                });
                log.info("downloading snapshot: serial={} session={} url={} expected_hash={} client={}", notificationSerial, sessionIdUUID, snapshotUrl, desiredSnapshotHash, httpClient.describe());
            }

            final byte[] snapshotContent = loadSnapshot(snapshotUrl, desiredSnapshotHash);

            final Document snapshotXmlDoc = documentBuilder.parse(new ByteArrayInputStream(snapshotContent));
            var doc = snapshotXmlDoc.getDocumentElement();

            validateSnapshotStructure(notificationSerial, sessionIdUUID, snapshotUrl, doc);

            var processPublishElementResult = processPublishElements(doc);

            return new RrdpSnapshotState(
                    snapshotUrl,
                    sessionIdUUID,
                    desiredSnapshotHash,
                    notificationSerial,
                    processPublishElementResult.objects,
                    processPublishElementResult.collisionCount
            );

        } catch (RRDPStructureException e) {
            throw e;
        } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException e) {
            // recall: IOException, ConnectException are subtypes of IOException
            throw new FetcherException(e);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Timeout")) {
                log.info("Timeout while loading RRDP repo: details={} url={}", httpClient, notificationUrl);
                throw new RrdpHttp.HttpTimeout(httpClient, notificationUrl, e);
            } else {
                throw e;
            }
        }
    }

    /**
     * @requires: sessionId is UUID version 4.
     */
    static void validateSnapshotStructure(BigInteger notificationSerial, UUID sessionId, String snapshotUrl, Element doc) throws XPathExpressionException, RRDPStructureException {
        // Check attributes of root snapshot element (mostly: that serial matches)
        var querySnapshot = XPathFactory.newDefaultInstance().newXPath().compile("/snapshot");
        var snapshotNodes = (NodeList) querySnapshot.evaluate(doc, XPathConstants.NODESET);
        // It is invariant that there is only one root element in an XML file, but it could still contain a different
        // root tag => 0
        if (snapshotNodes.getLength() != 1) {
            throw new RRDPStructureException(snapshotUrl, "No <snapshot>...</snapshot> root element found");
        } else {
            var item = snapshotNodes.item(0);
            try {
                var snapshotSerial = parseSerial(snapshotUrl, item);
                var snapshotSessionId = validateSessionIdUUIDv4(snapshotUrl, item);

                // transitively, equality implies that session-id argument is UUID version 4 as well.
                if (!sessionId.equals(snapshotSessionId)) {
                    throw new RRDPStructureException(snapshotUrl, "contained session-id=%s, expected=%s".formatted(sessionId, snapshotSessionId));
                }
                if (!notificationSerial.equals(snapshotSerial)) {
                    throw new RRDPStructureException(snapshotUrl, "contained serial=%d, expected=%d".formatted(snapshotSerial, notificationSerial));
                }
            } catch (NumberFormatException e) {
                throw new RRDPStructureException(snapshotUrl, "Invalid serial in <snapshot> tag.");
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
                .unordered()
                .map(item -> {
                    var objectUri = item.getAttributes().getNamedItem("uri").getNodeValue().intern();
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
                .entrySet()
                .stream().unordered()
                .map(item -> {
                    if (item.getValue().size() > 1) {
                        log.warn("Multiple objects for {}, keeping first element: {}", item.getKey(), item.getValue().stream().map(coll -> Sha256.asString(coll.getRight().bytes())).collect(Collectors.joining(", ")));
                        collisionCount.addAndGet(item.getValue().size() - 1);
                        return item.getValue().get(0);
                    }
                    return item.getValue().get(0);
                })
                .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));

        return new ProcessPublishElementResult(objects, collisionCount.get());
    }

    private static UUID validateSessionIdUUIDv4(String url, Node element) throws RRDPStructureException {
        var sessionId = element.getAttributes().getNamedItem("session_id").getNodeValue();
        try {
            var sessionUUID = UUID.fromString(sessionId); // throws IllegalArgumentException if not a valid UUID
            if (sessionUUID.version() != 4) {
                throw new RRDPStructureException(url, "session_id %s is not a valid UUIDv4 (version: %d)".formatted(sessionId, sessionUUID.version()));
            }
            return sessionUUID;
        } catch (IllegalArgumentException e) {
            throw new RRDPStructureException(url, "session_id %s is not a valid UUID".formatted(sessionId));
        }
    }

    private static BigInteger parseSerial(String url, Node element) throws  RRDPStructureException {
        var serial = element.getAttributes().getNamedItem("serial").getNodeValue();
        try {
            return new BigInteger(serial);
        } catch (NumberFormatException e) {
            throw new RRDPStructureException(url, "invalid serial '%s'".formatted(serial));
        }
    }


    record ProcessPublishElementResult(ImmutableMap<String, RpkiObject> objects, int collisionCount) {}

    public record RrdpSnapshotState(String snapshotUrl, UUID sessionId, String snapshotHash, BigInteger serial, ImmutableMap<String, RpkiObject> objects, int collisionCount){
        public long serialAsLong() {
            return serial.mod(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        }

    }
}