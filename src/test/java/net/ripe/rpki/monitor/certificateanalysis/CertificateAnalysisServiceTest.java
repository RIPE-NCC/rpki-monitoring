package net.ripe.rpki.monitor.certificateanalysis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.monitor.expiration.fetchers.RRDPStructureException;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotNotModifiedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.ripe.rpki.monitor.util.RrdpSampleContentUtil.rpkiObjects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;


@Execution(ExecutionMode.CONCURRENT)
@Slf4j
public class CertificateAnalysisServiceTest {
    public final static ImmutableResourceSet TEST_NET_1 = ImmutableResourceSet.parse("192.0.2.0/24");
    public final static ImmutableResourceSet TEST_NET_2 = ImmutableResourceSet.parse("198.51.100.0/24");
    public final static ImmutableResourceSet TEST_NET_3 = ImmutableResourceSet.parse("203.0.113.0/24");


    public static final String APNIC_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.apnic.net/repository/apnic-rpki-root-iana-origin.cer";
    public static final String RIPE_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer";
    public static final ImmutableResourceSet ALL_IPv4_RESOURCE_SET = ImmutableResourceSet.of(IpResource.ALL_IPV4_RESOURCES);

    private CertificateAnalysisConfig config;

    private MeterRegistry registry;

    private CertificateAnalysisService subject;

    @BeforeEach
    void setUp() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, SnapshotNotModifiedException {
        config = new CertificateAnalysisConfig();
        registry = new SimpleMeterRegistry();

        subject = new CertificateAnalysisService(config, Optional.empty(), registry);
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

        CertificateAnalysisService.printOverlaps(overlaps);

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
        var ripeObjects = rpkiObjects("rrdp-content/ripe/notification.xml", "rrdp-content/ripe/snapshot.xml");

        var overlaps = subject.process(ripeObjects);

        // Two overlapping pairs at time of data creation: delegated CAs in keyroll
        assertThat(overlaps).hasSize(2);

        // Two pairs that consist of distincs certificates
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.certificate.count").gauge().value()).isEqualTo(4);
        // Implies: parent-child overlap is not counted.
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.resource.count").gauge().value()).isEqualTo(2);
        assertThat(registry.get("rpkimonitoring.certificate.analysis.certificate.count").gauge().value()).isGreaterThan(20_000);
    }

    private Set<URI> extractSIAs(List<Set<CertificateEntry>> setsOfEntries) {
        return setsOfEntries.stream()
                .flatMap(Collection::stream)
                .map(cert -> cert.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST))
                .collect(Collectors.toSet());
    }

    /**
     * Pilot has test data with intermediate CAs. Use this to tests all base cases (filtering overlapping files, SIA filtering, etc.) and the metrics.
     */
    @Test
    void testCompareAndTrack_pilot_including_SIA_filter() throws ExecutionException, InterruptedException {
        var objects = rpkiObjects("rrdp/pilot/notification.xml.gz", "rrdp/pilot/snapshot.xml.gz");

        config.setRootCertificateUrl("rsync://localcert.ripe.net/ta/ripe-ncc-pilot.cer");
        config.setIgnoredOverlaps(List.of(
                new CertificateAnalysisConfig.IgnoredOverlap(Pattern.compile("rsync://localcert\\.ripe\\.net/repository/aca/[^/]*"), "")
        ));

        var overlaps = subject.process(objects);
        var sias = extractSIAs(overlaps);

        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.certificate.count").gauge().value()).isGreaterThan(300);
        assertThat(registry.get("rpkimonitoring.certificate.analysis.overlapping.resource.count").gauge().value()).isGreaterThan(1_000).isLessThan(3000);
        assertThat(registry.get("rpkimonitoring.certificate.analysis.certificate.count").gauge().value()).isEqualTo(504);

        // **dirty**: filter out a large chunk of the pilot repo
        config.setTrackedSias(List.of(Pattern.compile(".*/DEFAULT/[0-9].*")));
        var overlapsWithFilter = subject.process(objects);
        var filteredSias = extractSIAs(overlapsWithFilter);

        assertThat(filteredSias).hasSizeLessThan(sias.size());
        assertThat(sias).containsAll(filteredSias);
    }

    @Test
    void testSymmetricDifference() {
        var emptyDifferenceForFullOverlap = CertificateAnalysisService.symmetricDifference(ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES), ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES));
        assertThat(emptyDifferenceForFullOverlap).isEmpty();

        var disjunctInputs = CertificateAnalysisService.symmetricDifference(ImmutableResourceSet.of(IpResource.ALL_AS_RESOURCES), ALL_IPv4_RESOURCE_SET);
        assertThat(disjunctInputs).containsExactly(IpResource.ALL_AS_RESOURCES, IpResource.ALL_IPV4_RESOURCES);
    }

    @Test
    void testDetectsOverlap_overlapping_children() {
        var subject = new CertificateAnalysisService(config, Optional.empty(), new SimpleMeterRegistry());

        var root = new CertificateEntry("root", null, ALL_IPv4_RESOURCE_SET, "/");
        var childLhs = new CertificateEntry("lhs", null, TEST_NET_1, "/0/");
        var childMid = new CertificateEntry("mid", null, TEST_NET_2, "/1/");
        var childRhs = new CertificateEntry("rhs", null, TEST_NET_1, "/2/");

        var overlaps = subject.compareCertificates(List.of(root, childLhs, childMid, childRhs));

        // parent-child not detected, but the two children overlap
        assertThat(overlaps)
                .contains(Set.of(childLhs, childRhs))
                .noneMatch(overlap -> overlap.contains(root));
    }

    @Test
    void testAbortsOnManyOverlaps() {
        var subject = new CertificateAnalysisService(config, Optional.empty(), new SimpleMeterRegistry());

        var root = new CertificateEntry("root", null, ALL_IPv4_RESOURCE_SET, "/");

        // Have many overlaps, aborting individual certs
        var certs = new ArrayList<CertificateEntry>();
        certs.add(root);

        IntStream.range(0, 32).forEach(i -> {
            certs.add(new CertificateEntry("rsync://rsync.example.org/" + i + ".cer", null, ImmutableResourceSet.of(IpResource.parse((i % 255) + ".0.0.0/8")), "/" + i + "/"));
        });

        // should not throw but aborts all individual certs -> no result
        assertThat(subject.compareCertificates(certs)).hasSize(0);

        // Abort because of too many overlaps in total:
        var certsHighTotal = new ArrayList<CertificateEntry>();
        certsHighTotal.add(root);

        IntStream.range(0, 128).forEach(i -> {
            IntStream.range(0, 16).forEach(j -> {
                certsHighTotal.add(new CertificateEntry("rsync://rsync.example.org/" + i + "/" + j + ".cer", null, ImmutableResourceSet.of(IpResource.parse((i % 255) + ".0.0.0/8")).remove(IpResource.parse((i % 255) + "." + (j % 255) + ".0.0/16")), "/" + i + "/" + j + "/"));
            });
        });

        // Throws because it aborts completely
        assertThatThrownBy(() -> subject.compareCertificates(certsHighTotal))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testDetectsOverlap_sets_have_size_two() {
        var subject = new CertificateAnalysisService(config, Optional.empty(), new SimpleMeterRegistry());

        // 3 certs have the same resources,
        // so there could be a set of size 3 if you group by resource, however, we want pairs of
        // overlapping certificates.
        var childLhs = new CertificateEntry("lhs", null, TEST_NET_1, "/0/");
        var childMid = new CertificateEntry("mid", null, TEST_NET_1, "/1/");
        var childRhs = new CertificateEntry("rhs", null, TEST_NET_1, "/2/");

        var overlaps = subject.compareCertificates(List.of(childLhs, childMid, childRhs));

        assertThat(overlaps)
                // all (3 choose 3) = 3 possible subsets of pairs.
                .containsExactlyInAnyOrder(
                        Set.of(childLhs, childMid),
                        Set.of(childMid, childRhs),
                        Set.of(childLhs, childRhs)
                );
    }
}
