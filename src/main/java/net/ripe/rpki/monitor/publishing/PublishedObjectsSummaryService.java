package net.ripe.rpki.monitor.publishing;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import net.ripe.rpki.monitor.AppConfig;
import net.ripe.rpki.monitor.HasHashAndUri;
import net.ripe.rpki.monitor.expiration.RepositoryObjects;
import net.ripe.rpki.monitor.metrics.Metrics;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import net.ripe.rpki.monitor.service.core.CoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Setter
@Service
public class PublishedObjectsSummaryService {

    private final RepositoryObjects repositoryObjects;
    private final CoreClient rpkiCoreClient;
    private final AppConfig appConfig;

    private final Map<String, AtomicLong> counters = new HashMap<>();
    private final MeterRegistry registry;

    @Autowired
    public PublishedObjectsSummaryService(
        final RepositoryObjects repositoryObjects,
        final CoreClient rpkiCoreClient,
        final MeterRegistry registry,
        final AppConfig appConfig) {
        this.registry = registry;
        this.repositoryObjects = repositoryObjects;
        this.rpkiCoreClient = rpkiCoreClient;
        this.appConfig = appConfig;
    }

    /**
     * Get the diff of the published objects <b>and update the metrics</b>.
     *
     * @return
     */
    public Map<String, Set<FileEntry>> getPublishedObjectsDiff() {
        return getPublishedObjectsDiff(
            rpkiCoreClient.publishedObjects(),
            repositoryObjects.getObjects(appConfig.getRrdpConfig().getUrl()),
            repositoryObjects.getObjects(appConfig.getRsyncConfig().getOnPremiseUrl())
        );
    }

    Map<String, Set<FileEntry>> getRsyncDiff() {
        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        final String mainRsyncUrl = appConfig.getRsyncConfig().getOnPremiseUrl();
        /*
            Since we are comparing repositories coming from different servers, the URLs will
            be different in the server/port part. So we are going to compare only the path.
         */
        final var mainRepository = FileEntry.fromObjectsWithRelativeUrl(repositoryObjects.getObjects(mainRsyncUrl));
        for (var awsUrl : appConfig.getRsyncConfig().getAwsUrl()) {
            final var diffCount = getOrCreateDiffCounter(mainRsyncUrl, awsUrl);
            final var diffCountInv = getOrCreateDiffCounter(awsUrl, mainRsyncUrl);

            final var awsRepository = FileEntry.fromObjectsWithRelativeUrl(repositoryObjects.getObjects(awsUrl));
            final var diff = Sets.difference(mainRepository, awsRepository);
            final var diffInv = Sets.difference(awsRepository, mainRepository);

            diffCount.set(diff.size());
            diffCountInv.set(diffInv.size());

            var tag = diffTag(mainRsyncUrl, awsUrl);
            var tagInv = diffTag(awsUrl, mainRsyncUrl);
            diffs.put(tag, diff);
            diffs.put(tagInv, diffInv);
        }
        return diffs;
    }

    public <T1 extends HasHashAndUri, T2 extends HasHashAndUri>
    Map<String, Set<FileEntry>> getPublishedObjectsDiff(Collection<T1> coreObjects, Set<T2> rrdpObject_, Set<T2> rsyncObjects_) {

        final var tags = new String[]{"core", "rrdp", "rsync"};
        final Map<String, Set<FileEntry>> objects = new HashMap<>();
        objects.put(tags[0], FileEntry.fromObjects(coreObjects));
        objects.put(tags[1], FileEntry.fromObjects(rrdpObject_));
        objects.put(tags[2], FileEntry.fromObjects(rsyncObjects_));

        final Map<String, Set<FileEntry>> diffs = new HashMap<>();
        for (int i = 0; i < tags.length; i++) {
            final Set<FileEntry> objectsI = objects.get(tags[i]);
            var counter = getOrCreateCounter(tags[i]);
            counter.set(objectsI.size());
            for (int j = 0; j < i; j++) {
                final Set<FileEntry> objectsJ = objects.get(tags[j]);
                final var diff = Sets.difference(objectsI, objectsJ);
                final var diffInv = Sets.difference(objectsJ, objectsI);

                final var diffCount = getOrCreateDiffCounter(tags[i], tags[j]);
                final var diffCountInv = getOrCreateDiffCounter(tags[j], tags[i]);

                diffCount.set(diff.size());
                diffCountInv.set(diffInv.size());

                var tag = diffTag(tags[i], tags[j]);
                var tagInv = diffTag(tags[j], tags[i]);
                diffs.put(tag, diff);
                diffs.put(tagInv, diffInv);
            }
        }

        return diffs;
    }

    private AtomicLong getOrCreateDiffCounter(String lhs, String rhs) {
        final String tag = diffTag(lhs, rhs);
        AtomicLong diffCount = counters.get(tag);
        if (diffCount == null) {
            diffCount = new AtomicLong(0);
            counters.put(tag, diffCount);
            Metrics.buildObjectDiffGauge(registry, diffCount, lhs, rhs);
        }
        return diffCount;
    }

    private static String diffTag(final String lhs, final String rhs) {
        return lhs + "-diff-" + rhs;
    }

    private AtomicLong getOrCreateCounter(String tag) {
        AtomicLong diffCount = counters.get(tag);
        if (diffCount == null) {
            diffCount = new AtomicLong(0);
            counters.put(tag, diffCount);
            Metrics.buildObjectCountGauge(registry, diffCount, tag);
        }
        return diffCount;
    }
}
