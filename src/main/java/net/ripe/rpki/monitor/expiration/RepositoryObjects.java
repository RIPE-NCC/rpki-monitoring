package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableSet;
import lombok.Value;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class RepositoryObjects {
    private final Map<String, RepositoryContent> overallContent = new ConcurrentHashMap<>();

    public void setRepositoryObject(String repositoryUrl, final ConcurrentSkipListSet<RepoObject> repoObjects) {
        overallContent.put(repositoryUrl, new RepositoryContent(repoObjects));
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
        ConcurrentSkipListSet<RepoObject> objects;
    }
}
