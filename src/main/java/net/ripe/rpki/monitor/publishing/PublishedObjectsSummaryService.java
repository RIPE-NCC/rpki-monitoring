package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.*;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.SummaryService;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Setter
@Service
public class PublishedObjectsSummaryService {
    public final static String PUBLISHED_OBJECT_DIFF_DESCRIPTION = "Number of objects in <lhs> that are not in <rhs>";
    public final static String PUBLISHED_OBJECT_DIFF = "rpkimonitoring.published.objects.diff";
    public final static String PUBLISHED_OBJECT_COUNT_DESCRIPTION = "Number of published objects";
    public final static String PUBLISHED_OBJECT_COUNT = "rpkimonitoring.published.objects.count";

    private final SummaryService repositoryObjects;
    private final CoreClient rpkiCoreClient;

    private final AtomicLong inCoreNotInRRDPCount = new AtomicLong();
    private final AtomicLong inCoreNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRRDPNotInCoreCount = new AtomicLong();
    private final AtomicLong inRRDPNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRsyncNotInCoreCount = new AtomicLong();
    private final AtomicLong inRsyncNotInRRDPCount = new AtomicLong();

    private final AtomicLong rsyncObjectCount = new AtomicLong();
    private final AtomicLong rrdpObjectCount = new AtomicLong();
    private final AtomicLong publishedObjectsObjectCount = new AtomicLong();

    private final MeterRegistry registry;

    @Autowired
    public PublishedObjectsSummaryService(
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

        buildObjectCountGauge(rsyncObjectCount, "rsync");
        buildObjectCountGauge(publishedObjectsObjectCount, "core");
        buildObjectCountGauge(rrdpObjectCount, "rrdp");

    }

    private void buildObjectCountGauge(AtomicLong gauge, String source) {
        Gauge.builder(PUBLISHED_OBJECT_COUNT, gauge::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("source", source)
                .register(registry);
    }

    private void buildObjectDiffGauge(AtomicLong counter, String lhs, String rhs) {
        Gauge.builder(PUBLISHED_OBJECT_DIFF, counter::get)
                .description(PUBLISHED_OBJECT_DIFF_DESCRIPTION)
                .tag("lhs", lhs)
                .tag("rhs", rhs)
                .register(registry);
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public PublicationDiff getPublishedObjectsDiff() {
        final var rpkiCoreObjects = FileEntry.fromObjects(rpkiCoreClient.publishedObjects());
        final var rrdpObjects = FileEntry.fromObjects(repositoryObjects.getRrdpObjects());
        final var rsyncObjects = FileEntry.fromObjects(repositoryObjects.getRsynObjects());

        publishedObjectsObjectCount.set(rpkiCoreObjects.size());
        rsyncObjectCount.set(rsyncObjects.size());
        rrdpObjectCount.set(rrdpObjects.size());


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
                .inCoreNotInRRDP(inCoreNotInRRDP)
                .inCoreNotInRsync(inCoreNotInRsync)
                .inRRDPNotInCore(inRRDPNotInCore)
                .inRRDPNotInRsync(inRRDPNotInRsync)
                .inRsyncNotInCore(inRsyncNotInCore)
                .inRsyncNotInRRDP(inRsyncNotInRRDP)
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
