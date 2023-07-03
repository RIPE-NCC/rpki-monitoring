package net.ripe.rpki.monitor.certificateanalysis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties("certificate.analysis")
@Data
public class CertificateAnalysisConfig {
    private List<IgnoredOverlap> ignoredOverlaps = List.of();

    private String rootCertificateUrl;

    public record IgnoredOverlap(Pattern regex, String description) {}

    public boolean isIgnoredFileName(String fileName) {
        return ignoredOverlaps.stream().anyMatch(ignoredOverlap -> ignoredOverlap.regex().matcher(fileName).matches());
    }
}

