package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;

record CertificateEntry(String uri, X509ResourceCertificate certificate, ImmutableResourceSet resources, String reachabilityPath) {
    public CertificateEntry(String uri, X509ResourceCertificate certificate, String path) {
        this(uri, certificate, ImmutableResourceSet.of(certificate.getResources()), path);
    }

    public static boolean areAncestors(CertificateEntry lhs, CertificateEntry rhs) {
        return !lhs.reachabilityPath.equals(rhs.reachabilityPath) && (lhs.reachabilityPath.startsWith(rhs.reachabilityPath) || rhs.reachabilityPath.startsWith(lhs.reachabilityPath));
    }
}
