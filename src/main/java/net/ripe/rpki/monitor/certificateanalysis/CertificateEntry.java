package net.ripe.rpki.monitor.certificateanalysis;

import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;

import java.util.Objects;

/**
 * The entry for a certificate in an explored tree of certificates.
 *
 * The certificate itself is ignored for equals/hashCode because even if a certificate would be reached multiple times,
 * it would have multiple distinct paths.
 *
 * <emph>Prevents reflection based equals/hashcode in certificates</emph>. Still calls this via the resources.
 */
record CertificateEntry(@NonNull String uri, X509ResourceCertificate certificate, @NonNull ImmutableResourceSet resources, @NonNull String reachabilityPath) {
    public CertificateEntry(String uri, @NonNull X509ResourceCertificate certificate, String path) {
        this(uri, certificate, ImmutableResourceSet.of(certificate.getResources()), path);
    }

    public static boolean areAncestors(CertificateEntry lhs, CertificateEntry rhs) {
        return !lhs.reachabilityPath.equals(rhs.reachabilityPath) && (lhs.reachabilityPath.startsWith(rhs.reachabilityPath) || rhs.reachabilityPath.startsWith(lhs.reachabilityPath));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateEntry that = (CertificateEntry) o;
        return Objects.equals(uri, that.uri) && Objects.equals(resources, that.resources) && Objects.equals(reachabilityPath, that.reachabilityPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, reachabilityPath);
    }
}
