package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;

record CertificateEntry(String uri, X509ResourceCertificate certificate, ImmutableResourceSet resources) {

    public CertificateEntry(String uri, X509ResourceCertificate certificate) {
        this(uri, certificate, ImmutableResourceSet.of(certificate.getResources()));
    }

    public static CertificateEntry ofEncodedResourceCertificate(String uri, byte[] encoded) {
        ValidationResult validationResult = ValidationResult.withLocation(uri);
        var parser = new X509ResourceCertificateParser();
        parser.parse(validationResult, encoded);
        return new CertificateEntry(uri, parser.getCertificate());
    }


}
