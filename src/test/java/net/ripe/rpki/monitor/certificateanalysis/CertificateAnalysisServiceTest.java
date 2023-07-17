package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.rpki.monitor.expiration.fetchers.RRDPStructureException;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpSnapshotClient;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotNotModifiedException;
import net.ripe.rpki.monitor.fetchers.RrdpSnapshotClientTest;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@Slf4j
public class CertificateAnalysisServiceTest {
    public final static ImmutableResourceSet TEST_NET_1 = ImmutableResourceSet.parse("192.0.2.0/24");
    public final static ImmutableResourceSet TEST_NET_2 = ImmutableResourceSet.parse("198.51.100.0/24");
    public final static ImmutableResourceSet TEST_NET_3 = ImmutableResourceSet.parse("203.0.113.0/24");


    public static final String APNIC_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.apnic.net/repository/apnic-rpki-root-iana-origin.cer";
    public static final String RIPE_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer";

    private CertificateAnalysisConfig config;

    private MeterRegistry registry;

    private CertificateAnalysisService subject;

    @BeforeEach
    void setUp() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, SnapshotNotModifiedException {
        config = new CertificateAnalysisConfig();
        registry = new SimpleMeterRegistry();

        subject = new CertificateAnalysisService(config, Optional.empty(), registry);
    }

    @SneakyThrows
    ImmutableMap<String, RpkiObject> rpkiObjects(String notificationClasspathPath, String snapshotClasspathPath) {
        // dirty setup to get RRDP objects from the mock data
        var mockHttp = mock(RrdpHttp.class);
        when(mockHttp.fetch(any())).thenReturn(
                new ClassPathResource(notificationClasspathPath).getInputStream().readAllBytes(),
                new ClassPathResource(snapshotClasspathPath).getInputStream().readAllBytes()
        );
        when(mockHttp.transformHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        return new RrdpSnapshotClient(mockHttp).fetchObjects(RrdpSnapshotClientTest.EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty()).objects();
    }

    @Test
    void testExpandCertificates_ripe() throws ExecutionException, InterruptedException {
        var objects = rpkiObjects("rrdp-content/ripe/notification.xml", "rrdp-content/ripe/snapshot.xml");
        // Top-down exploration via manifests
        var resourceCertificates = ForkJoinPool.commonPool().submit(new ExtractRpkiCertificateSpan(objects, RIPE_TRUST_ANCHOR_CERTIFICATE_URL)).get()
                .collect(Collectors.toMap(
                        entry -> entry.uri(),
                        entry -> entry
                ));
        log.info("RIPE: Expanded {} RPKI certificates", resourceCertificates.size());

        assertThat(resourceCertificates).containsKey(RIPE_TRUST_ANCHOR_CERTIFICATE_URL);

        // one TA certificate, many members under another prefix.
        assertThat(resourceCertificates.keySet().stream().filter(key -> key.startsWith("rsync://rpki.ripe.net/ta")).count()).isOne();
        assertThat(resourceCertificates.keySet().stream().filter(key -> key.startsWith("rsync://rpki.ripe.net/repository")).count()).isGreaterThan(20_000);

        // Count the files per level.
        var certificatesPerLevel = resourceCertificates.values().stream()
                .collect(Collectors.groupingBy(entry -> entry.reachabilityPath().split("/").length, Collectors.counting()));

        certificatesPerLevel.forEach((key, value) -> log.info("level {} |certs|: {}", key, value));
        // Validate that we have multiple levels that where one level has > 10000 certs
        assertThat(certificatesPerLevel)
                .hasSizeGreaterThan(3)
                .anySatisfy((level, count) -> assertThat(count).isGreaterThan(10_000))
                // Check that the levels make sense.
                // it is invariant that the keys of a map are unique.
                // levels start at 0
                .allSatisfy((level, count) -> assertThat(level).isNotNegative())
                // no number higher than the number of elements
                .allSatisfy((level, count) -> assertThat(level).isLessThanOrEqualTo(certificatesPerLevel.size()));
    }

    @Test
    void testExpandCertificates_apnic() throws ExecutionException, InterruptedException {
        var objects = rpkiObjects("rrdp-content/apnic/notification.xml", "rrdp-content/apnic/snapshot.xml");
        // Top-down exploration via manifests
        var resourceCertificates = ForkJoinPool.commonPool().submit(new ExtractRpkiCertificateSpan(objects, APNIC_TRUST_ANCHOR_CERTIFICATE_URL)).get()
                .collect(Collectors.toMap(
                        entry -> entry.uri(),
                        entry -> entry
                ));
        log.info("APNIC: Expanded {} RPKI certificates", resourceCertificates.size());

        assertThat(resourceCertificates)
                .containsKey(APNIC_TRUST_ANCHOR_CERTIFICATE_URL)
                // root + all CAs are in one prefix, not able to easily filter.
                .hasSizeGreaterThan(5_000);
    }

    /**
     * Use APNIC because it has intermediate CAs as well as multiple child certificates in some nodes, which
     * is a worse case for the algorithm than a "clean" tree.
     */
    @Test
    void testCompareAndTrackMetrics_apnic() {
        config.setRootCertificateUrl(APNIC_TRUST_ANCHOR_CERTIFICATE_URL);

        var overlaps = subject.process(rpkiObjects("rrdp-content/apnic/notification.xml", "rrdp-content/apnic/snapshot.xml"));

        // Because of multiple active certificates for certain members (?)
        assertThat(overlaps).hasSizeGreaterThan(10);

        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.certificate.count").gauge().value()).isGreaterThan(13);
        // Implies: parent-child overlap is not counted.
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.resource.count").gauge().value()).isGreaterThan(42);
        assertThat(registry.get("rpkimonitoring.certificate.analysis.certificate.count").gauge().value()).isGreaterThan(5_000);
    }

    @Test
    void testCompareAndTrackMetrics_ripe() {
        config.setRootCertificateUrl(RIPE_TRUST_ANCHOR_CERTIFICATE_URL);

        var overlaps = subject.process(rpkiObjects("rrdp-content/ripe/notification.xml", "rrdp-content/ripe/snapshot.xml"));

        // Two overlapping pairs at time of data creation
        assertThat(overlaps).hasSize(2);

        // Two pairs that consist of distincs certificates
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.certificate.count").gauge().value()).isEqualTo(4);
        // Implies: parent-child overlap is not counted.
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.resource.count").gauge().value()).isEqualTo(2);
        assertThat(registry.get("rpkimonitoring.certificate.analysis.certificate.count").gauge().value()).isGreaterThan(20_000);
    }

    @Test
    void testSymmetricDifference() {
        var emptyDifferenceForFullOverlap = CertificateAnalysisService.symmetricDifference(ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES), ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES));
        assertThat(emptyDifferenceForFullOverlap).isEmpty();

        var disjunctInputs = CertificateAnalysisService.symmetricDifference(ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES), ImmutableResourceSet.of(IpResource.ALL_IPV4_RESOURCES));
        assertThat(disjunctInputs).containsExactly(IpResource.ALL_AS_RESOURCES, IpResource.ALL_IPV4_RESOURCES);
    }

    @Test
    void testDetectsOverlap_overlapping_children() {
        var subject = new CertificateAnalysisService(config, Optional.empty(), new SimpleMeterRegistry());

        var root = new CertificateEntry("root", null, ImmutableResourceSet.of(IpResource.ALL_IPV4_RESOURCES), "/");
        var childLhs = new CertificateEntry("lhs", null, TEST_NET_1, "/0/");
        var childMid = new CertificateEntry("mid", null, TEST_NET_2, "/1/");
        var childRhs = new CertificateEntry("rhs", null, TEST_NET_1, "/2/");

        var overlaps = subject.compareCertificates(List.of(root, childLhs, childMid, childRhs));

        // parent-child not detected, but the two children overlap
        assertThat(overlaps)
                .contains(Set.of(childLhs, childRhs))
                .noneMatch(overlap -> overlap.contains(root));
    }
}
