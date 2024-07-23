package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static net.ripe.rpki.monitor.certificateanalysis.CertificateAnalysisTestValues.TEST_NET_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CertificateAnalysisConfigTest {
    private CertificateAnalysisConfig subject;

    static X509Certificate mockCertWithNotBefore(Instant notBefore) {
        var mock = mock(X509Certificate.class);
        when(mock.getNotBefore()).thenReturn(Date.from(notBefore));
        return mock;
    }

    @BeforeEach
    public void setUp() {
        subject = new CertificateAnalysisConfig();
    }

    @Test
    public void testTrackedSIACheck() {
        var mockCert = mock(X509ResourceCertificate.class);
        when(mockCert.findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST)).thenReturn(URI.create("rsync://rpki.example.org/repository/first.mft"));
        when(mockCert.getResources()).thenReturn(new IpResourceSet(TEST_NET_1));

        var delegatedMockCert = mock(X509ResourceCertificate.class);
        when(delegatedMockCert.findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST)).thenReturn(URI.create("rsync://delegated-ca.example.org/repository/my.mft"));
        when(delegatedMockCert.getResources()).thenReturn(new IpResourceSet(TEST_NET_1));

        var entries = Set.of(
            new CertificateEntry("rsync://rpki.example.org/ta/root.cer", mockCert, "/0/"),
            new CertificateEntry("rsync://rpki.example.org/repository/delegated.cer", delegatedMockCert, "/0/0/")
        );

        // Test empty set of tracked SIAs
        assertThat(subject.hasOnlyTrackedSIA().test(entries)).isTrue();

        // Test matching tracked SIA
        subject.setTrackedSias(List.of(java.util.regex.Pattern.compile("rsync://.*.example.org/.*")));
        assertThat(subject.hasOnlyTrackedSIA().test(entries)).isTrue();

        // Also with multiple entries
        subject.setTrackedSias(List.of(java.util.regex.Pattern.compile("rsync://.*.example.org/.*"), Pattern.compile("rsync://paas.example.org/.*")));
        assertThat(subject.hasOnlyTrackedSIA().test(entries)).isTrue();

        // But if **not all** SIAs match the set is dropped - delegated.example.org does not match
        subject.setTrackedSias(List.of(java.util.regex.Pattern.compile("rsync://rpki.example.org/ta/.*")));
        assertThat(subject.hasOnlyTrackedSIA().test(entries)).isFalse();

        // Test non-matching tracked SIA
        subject.setTrackedSias(List.of(java.util.regex.Pattern.compile("rsync://otherhost.example.org/.*")));
        assertThat(subject.hasOnlyTrackedSIA().test(entries)).isFalse();
    }

    @Test
    public void testGracePeriodCheck() {
        var now = Instant.now();

        var notBefore3d1s = mockCertWithNotBefore(now.minus(Duration.ofDays(3)).minusSeconds(1));
        var notBefore3h1s = mockCertWithNotBefore(now.minus(Duration.ofHours(3)).minusSeconds(1));

        var mockCert = mock(X509ResourceCertificate.class);
        when(mockCert.getCertificate()).thenReturn(notBefore3d1s);
        when(mockCert.getResources()).thenReturn(new IpResourceSet(TEST_NET_1));

        var delegatedMockCert = mock(X509ResourceCertificate.class);
        when(delegatedMockCert.getCertificate()).thenReturn(notBefore3h1s);
        when(delegatedMockCert.getResources()).thenReturn(new IpResourceSet(TEST_NET_1));

        var entries = Set.of(
            new CertificateEntry("rsync://rpki.example.org/ta/root.cer", mockCert, "/0/"),
            new CertificateEntry("rsync://rpki.example.org/repository/delegated.cer", delegatedMockCert, "/0/0/")
        );

        // When grace period is not present, any pair of certificates is reported
        subject.setKeyrollPublicationPointGracePeriod(null);
        assertThat(subject.hasAnyCertificateAfterGracePeriodStarts(now).test(entries)).isFalse()
                .withFailMessage("When grace period is not set, nothing should be reported as being in grace period");

        subject.setKeyrollPublicationPointGracePeriod(Duration.ofSeconds(0));
        assertThat(subject.hasAnyCertificateAfterGracePeriodStarts(now).test(entries)).isFalse();

        // Also _right before_ the edge
        subject.setKeyrollPublicationPointGracePeriod(Duration.ofHours(3));
        assertThat(subject.hasAnyCertificateAfterGracePeriodStarts(now).test(entries)).isFalse();

        // When grace period is after the first cert, no pair is reported
        subject.setKeyrollPublicationPointGracePeriod(Duration.ofHours(3).plusSeconds(2));
        assertThat(subject.hasAnyCertificateAfterGracePeriodStarts(now).test(entries)).isTrue();

        // Also applies when both are in the period
        subject.setKeyrollPublicationPointGracePeriod(Duration.ofDays(4));
        assertThat(subject.hasAnyCertificateAfterGracePeriodStarts(now).test(entries)).isTrue();

        // When there are more than two certs (should not happen) we keep as soon as one matches.
        var thirdMockCert = mock(X509ResourceCertificate.class);
        when(thirdMockCert.getCertificate()).thenReturn(notBefore3d1s);
        when(thirdMockCert.getResources()).thenReturn(new IpResourceSet(TEST_NET_1));
    }
}
