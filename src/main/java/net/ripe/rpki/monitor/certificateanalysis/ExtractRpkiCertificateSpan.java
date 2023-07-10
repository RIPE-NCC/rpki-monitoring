package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateParser;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
class ExtractRpkiCertificateSpan extends RecursiveTask<Stream<CertificateEntry>> {
    private final ImmutableMap<String, RpkiObject> rpkiObjects;

    private final String certificateUrl;
    private final String path;

    public ExtractRpkiCertificateSpan(ImmutableMap<String, RpkiObject> rpkiObjects, String certificateUrl) {
        this(rpkiObjects, certificateUrl, "/");
    }

    private Optional<X509ResourceCertificate> parseCertificate(byte[] encoded) {
            ValidationResult validationResult = ValidationResult.withLocation(certificateUrl);
            var cert = X509CertificateParser.parseCertificate(validationResult, encoded);

            boolean router = cert.isRouter();
            if (!router) {
                var parser = new X509ResourceCertificateParser();
                parser.parse(validationResult, encoded);
                if (parser.isSuccess()) {
                    return Optional.of(parser.getCertificate());
                } else {
                    log.error("Error when parsing {}", certificateUrl);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
    }



    @Override
    protected Stream<CertificateEntry> compute() {
        var cert = rpkiObjects.get(certificateUrl);
        if (cert == null) {
            log.warn("No certificate at {}", certificateUrl);
            return Stream.empty();
        }

        var maybeCertificate = parseCertificate(cert.bytes());

        return maybeCertificate.map(certificate -> {
            var thisEntry = new CertificateEntry(certificateUrl, certificate, path);

            return Stream.concat(
                    Stream.of(thisEntry),
                    computeForManifestUrl(certificate.getManifestUri().toString())
            );
        }).orElse(Stream.empty());
    }

    private Stream<CertificateEntry> computeForManifestUrl(String manifestUrl) {
        var manifest = rpkiObjects.get(manifestUrl);

        if (manifest == null) {
            return Stream.empty();
        }


        var manifestParser = new ManifestCmsParser();
        manifestParser.parse(manifestUrl, manifest.bytes());

        if (!manifestParser.isSuccess()) {
            log.error("Error when parsing {}", manifestUrl);
            return Stream.empty();
        }
        return ForkJoinTask.invokeAll(createSubtasks(manifestUrl, manifestParser.getManifestCms()))
                        .stream()
                        .flatMap(ForkJoinTask::join);
    }

    private static String relativeUrl(String base, String fileName) {
        var tokens = base.split("/");

        tokens[tokens.length - 1] = fileName;
        return Joiner.on("/").join(tokens);
    }

    private Collection<ExtractRpkiCertificateSpan> createSubtasks(String manifestUrl, ManifestCms manifestCms) {
        return Streams.mapWithIndex(manifestCms.getFileNames().stream().filter(name -> name.endsWith(".cer")), (filename, idx) ->
                new ExtractRpkiCertificateSpan(rpkiObjects, relativeUrl(manifestUrl, filename), path + idx + "/")
        ).toList();
    }
}
