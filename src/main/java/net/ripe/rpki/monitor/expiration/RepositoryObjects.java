package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.Value;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Service
public class RepositoryObjects {
    @Autowired
    private ObjectExpirationMetrics objectExpirationMetrics;

    private final Map<String, RepositoryContent> overallContent = new ConcurrentHashMap<>();

    public void setRepositoryObject(String repositoryUrl, final SortedSet<RepoObject> repoObjects) {
        final var content = new RepositoryContent(repoObjects);
        overallContent.put(repositoryUrl, content);

        objectExpirationMetrics.trackExpiration(repositoryUrl, content);
    }

    public Set<RepoObject> geRepositoryObjectsAboutToExpire(String repositoryUrl, final int inHours) {
        final RepositoryContent repositoryContent = overallContent.get(repositoryUrl);
        if (repositoryContent == null) {
            return ImmutableSet.of();
        }
        final RepoObject upTo = RepoObject.fictionalObjectExpiringOn(DateTime.now().plusHours(inHours).toDate());
        return repositoryContent.objects.headSet(upTo);
    }

    public Set<RepoObject> getObjects(String repositoryUrl) {
        final RepositoryContent repositoryContent = overallContent.get(repositoryUrl);
        if (repositoryContent == null) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(repositoryContent.objects);
    }

    @Value
    public static class RepositoryContent {
        SortedSet<RepoObject> objects;
    }
}
