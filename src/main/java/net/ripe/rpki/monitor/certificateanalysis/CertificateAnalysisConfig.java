package net.ripe.rpki.monitor.certificateanalysis;

import lombok.Data;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties("certificate.analysis")
@Data
public class CertificateAnalysisConfig {
    private List<IgnoredOverlap> ignoredOverlaps = List.of();

    /**
     * SIA for which overlaps are tracked (we want to ignore duplicates for delegated CAs that have multiple active certificates).
     */
    private List<Pattern> trackedSias = List.of();

    private boolean enabled;

    private String rootCertificateUrl;

    /**
     * Grace period during which multiple certificates within the same publication point are allowed to overlap
     * <emph>with identical resources</emph> (e.g. keyroll).
     */
    private Duration keyrollPublicationPointGracePeriod;

    public record IgnoredOverlap(Pattern regex, String description) {}

    /**
     * For an overlap to be tracked;
     *   * There need to be no configured tracked overlaps, or
     *   * For at least <emph>one</emph> certificate the SIA is tracked.
     */
    public Predicate<Set<CertificateEntry>> hasOnlyTrackedSIA() {
        return (entries) -> trackedSias.isEmpty() || entries.stream()
                    .map(cert -> cert.certificate().findFirstSubjectInformationAccessByMethod(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST).toString())
                    .allMatch(sia -> trackedSias.stream().anyMatch(matcher -> matcher.matcher(sia).matches()));
    }

    /**
     * Does the set of entries contain an overlap where both items are outside the grace period?
     * If no threshold is set, the set is accepted as well.
     */
    public Predicate<Set<CertificateEntry>> hasAnyCertificateAfterGracePeriodStarts(Instant now) {
        return (entries) -> entries.stream()
                .map(CertificateEntry::certificate)
                .anyMatch(cert -> (keyrollPublicationPointGracePeriod != null && cert.getCertificate().getNotBefore().toInstant().isAfter(now.minus(keyrollPublicationPointGracePeriod))));
    }

    public boolean isIgnoredFileName(String fileName) {
        return ignoredOverlaps.stream().anyMatch(ignore -> ignore.regex.matcher(fileName).matches());
    }
}

