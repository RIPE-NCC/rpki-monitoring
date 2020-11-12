package net.ripe.rpki.monitor.compare;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.expiration.RepoObject;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Fetches two rsync repositories, compares them and reports the result.
 */
@Slf4j
@Service
public class RsyncComparisonService {

    private final MeterRegistry registry;
    private final RsyncConfig rsyncConfig;

    private final AtomicLong onAwsNotOnPremiseCount = new AtomicLong();
    private final AtomicLong onPremiseNotOnAwsCount = new AtomicLong();

    @Autowired
    public RsyncComparisonService(MeterRegistry registry,
                                  RsyncConfig rsyncConfig) {
        this.registry = registry;
        this.rsyncConfig = rsyncConfig;

        Metrics.buildObjectDiffGauge(registry, onAwsNotOnPremiseCount, "on-aws", "rrdp");
        Metrics.buildObjectDiffGauge(registry, onPremiseNotOnAwsCount, "core", "rsync");
    }

    void getRsyncComparison() {
//        final Set<RepoObject> rrdpObjects = summaryService.getRrdpObjects();
//        final Set<RepoObject> rsynObjects = summaryService.getRsynObjects();

    }

    @Builder
    @lombok.Value
    public static class RsyncDiff {
        private Set<FileEntry> onAwsNotOnPremise;
        private Set<FileEntry> onPremiseNotOnAws;
    }
}
