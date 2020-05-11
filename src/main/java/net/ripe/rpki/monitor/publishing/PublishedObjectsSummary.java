package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.SummaryService;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Data
@Service
public class PublishedObjectsSummary {
    public final static String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public final static String PUBLISHED_OBJECT_DIFF = "published.objects.diff";

    private final SummaryService repositoryObjects;
    private final CoreClient rpkiCoreClient;

    private final AtomicLong inCoreNotInRRDPCount = new AtomicLong();
    private final AtomicLong inCoreNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRRDPNotInCoreCount = new AtomicLong();
    private final AtomicLong inRRDPNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRsyncNotInCoreCount = new AtomicLong();
    private final AtomicLong inRsyncNotInRRDPCount = new AtomicLong();

    private final MeterRegistry registry;

    @Autowired
    public PublishedObjectsSummary(
            final SummaryService repositoryObjects,
            final CoreClient rpkiCoreClient,
            final MeterRegistry registry
    ) {
        this.repositoryObjects = repositoryObjects;
        this.rpkiCoreClient = rpkiCoreClient;
        this.registry = registry;

        buildObjectDiffGauge(inCoreNotInRRDPCount, "core", "rrdp");
        buildObjectDiffGauge(inCoreNotInRsyncCount, "core", "rsync");
        buildObjectDiffGauge(inRRDPNotInCoreCount, "rrdp", "core");
        buildObjectDiffGauge(inRRDPNotInRsyncCount, "rrdp", "rsync");
        buildObjectDiffGauge(inRsyncNotInCoreCount, "rsync", "core");
        buildObjectDiffGauge(inRsyncNotInRRDPCount, "rsync", "rrdp");
    }

    private void buildObjectDiffGauge(AtomicLong counter, String lhs, String rhs) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", lhs)
                .tag("rhs", rhs)
                .register(registry);
    }

    private static <T extends HasHashAndUri> Set<FileEntry> collectUriHashTuples(Collection<T> inp) {
        return inp.stream()
                .map(FileEntry::from)
                .collect(Collectors.toUnmodifiableSet());
    }

    public PublicationDiff compare() {
        final var rpkiCoreObjects = collectUriHashTuples(rpkiCoreClient.publishedObjects());
        final var rrdpObjects = collectUriHashTuples(repositoryObjects.getRrdpObjects());
        final var rsyncObjects = collectUriHashTuples(repositoryObjects.getRsynObjects());

        final var inCoreNotInRRDP = Sets.difference(rpkiCoreObjects, rrdpObjects);
        final var inCoreNotInRsync = Sets.difference(rpkiCoreObjects, rsyncObjects);
        final var inRRDPNotInCore = Sets.difference(rrdpObjects, rpkiCoreObjects);
        final var inRRDPNotInRsync = Sets.difference(rrdpObjects, rsyncObjects);
        final var inRsyncNotInCore = Sets.difference(rsyncObjects, rpkiCoreObjects);
        final var inRsyncNotInRRDP = Sets.difference(rsyncObjects, rrdpObjects);

        inCoreNotInRRDPCount.set(inCoreNotInRRDP.size());
        inCoreNotInRsyncCount.set(inCoreNotInRsync.size());
        inRRDPNotInCoreCount.set(inRRDPNotInCore.size());
        inRRDPNotInRsyncCount.set(inRRDPNotInRsync.size());
        inRsyncNotInCoreCount.set(inRsyncNotInCore.size());
        inRsyncNotInRRDPCount.set(inRsyncNotInRRDP.size());

        return PublicationDiff.builder()
                .inCoreNotInRRDP(Sets.difference(rpkiCoreObjects, rrdpObjects))
                .inCoreNotInRsync(Sets.difference(rpkiCoreObjects, rsyncObjects))
                .inRRDPNotInCore(Sets.difference(rrdpObjects, rpkiCoreObjects))
                .inRRDPNotInRsync(Sets.difference(rrdpObjects, rsyncObjects))
                .inRsyncNotInCore(Sets.difference(rsyncObjects, rpkiCoreObjects))
                .inRsyncNotInRRDP(Sets.difference(rsyncObjects, rrdpObjects))
                .build();
    }

    @Builder
    @Value
    public static class PublicationDiff {
        private Set<FileEntry> inCoreNotInRRDP;
        private Set<FileEntry> inCoreNotInRsync;
        private Set<FileEntry> inRsyncNotInRRDP;
        private Set<FileEntry> inRsyncNotInCore;
        private Set<FileEntry> inRRDPNotInRsync;
        private Set<FileEntry> inRRDPNotInCore;
    }
}
