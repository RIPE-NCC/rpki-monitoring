package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Setter
@Service
public class PublishedObjectsSummaryService {

    private final RepositoryObjects repositoryObjects;
    private final CoreClient rpkiCoreClient;
    private final AppConfig appConfig;

    private final AtomicLong inCoreNotInRRDPCount = new AtomicLong();
    private final AtomicLong inCoreNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRRDPNotInCoreCount = new AtomicLong();
    private final AtomicLong inRRDPNotInRsyncCount = new AtomicLong();
    private final AtomicLong inRsyncNotInCoreCount = new AtomicLong();
    private final AtomicLong inRsyncNotInRRDPCount = new AtomicLong();

    private final AtomicLong rsyncObjectCount = new AtomicLong();
    private final AtomicLong rrdpObjectCount = new AtomicLong();
    private final AtomicLong publishedObjectsObjectCount = new AtomicLong();

    @Autowired
    public PublishedObjectsSummaryService(
        final RepositoryObjects repositoryObjects,
        final CoreClient rpkiCoreClient,
        final MeterRegistry registry,
        final AppConfig appConfig) {
        this.repositoryObjects = repositoryObjects;
        this.rpkiCoreClient = rpkiCoreClient;
        this.appConfig = appConfig;

        Metrics.buildObjectDiffGauge(registry, inCoreNotInRRDPCount, "core", "rrdp");
        Metrics.buildObjectDiffGauge(registry, inCoreNotInRsyncCount, "core", "rsync");
        Metrics.buildObjectDiffGauge(registry, inRRDPNotInCoreCount, "rrdp", "core");
        Metrics.buildObjectDiffGauge(registry, inRRDPNotInRsyncCount, "rrdp", "rsync");
        Metrics.buildObjectDiffGauge(registry, inRsyncNotInCoreCount, "rsync", "core");
        Metrics.buildObjectDiffGauge(registry, inRsyncNotInRRDPCount, "rsync", "rrdp");

        Metrics.buildObjectCountGauge(registry, rsyncObjectCount, "rsync");
        Metrics.buildObjectCountGauge(registry, publishedObjectsObjectCount, "core");
        Metrics.buildObjectCountGauge(registry, rrdpObjectCount, "rrdp");
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     */
    public PublicationDiff getPublishedObjectsDiff() {
        return getPublishedObjectsDiff(
            rpkiCoreClient.publishedObjects(),
            repositoryObjects.getObjects(appConfig.getRrdpUrl()),
            repositoryObjects.getObjects(appConfig.getRsyncUrl())
        );
    }

    public <T1 extends HasHashAndUri, T2 extends HasHashAndUri> PublicationDiff
    getPublishedObjectsDiff(Collection<T1> coreObjects, Set<T2> rrdpObject_, Set<T2> rsyncObjects_) {
        var rpkiCoreObjects = FileEntry.fromObjects(coreObjects);
        var rrdpObjects = FileEntry.fromObjects(rrdpObject_);
        var rsyncObjects = FileEntry.fromObjects(rsyncObjects_);

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
