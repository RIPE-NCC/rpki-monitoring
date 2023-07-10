package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CertificateEntryTest {
    private X509ResourceCertificate mockCertificate;

    @BeforeEach
    void setUp() {
        mockCertificate = mock(X509ResourceCertificate.class);
        when(mockCertificate.getResources()).thenReturn(new IpResourceSet(IpResource.ALL_AS_RESOURCES));
    }

    @Test
    public void testAncestor() {
        var root = new CertificateEntry("n1", mockCertificate, "/");
        var child = new CertificateEntry("child", mockCertificate, "/child");
        var subChild = new CertificateEntry("subChild", mockCertificate, "/child/subChild");

        var otherChild = new CertificateEntry("otherChild", mockCertificate, "/otherChild");

        var allNodes = List.of(root, child, subChild, otherChild);

        // Self-reference is not an ancestor
        allNodes.forEach(node -> assertThat(CertificateEntry.areAncestors(node, node)).isFalse());

        // Symmetric
        assertThat(CertificateEntry.areAncestors(root, child)).isTrue();
        assertThat(CertificateEntry.areAncestors(child, root)).isTrue();

        // Transitive: child nodes are still acestors relative to parent
        assertThat(CertificateEntry.areAncestors(child, subChild)).isTrue();

        assertThat(CertificateEntry.areAncestors(root, subChild)).isTrue();
        assertThat(CertificateEntry.areAncestors(subChild, root)).isTrue();

        // Non-ancestor, both symmetric cases:
        assertThat(CertificateEntry.areAncestors(child, otherChild)).isFalse();
        assertThat(CertificateEntry.areAncestors(otherChild, child)).isFalse();
    }
}
