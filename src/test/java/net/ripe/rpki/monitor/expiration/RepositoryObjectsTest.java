package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableSortedSet;
import net.ripe.rpki.monitor.metrics.ObjectExpirationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.SortedSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RepositoryObjectsTest {
    private RepositoryObjects repositoryObjects;
    private ObjectExpirationMetrics expirationMetrics;

    public final static String OTHER_REPO_URL = "rsync://rpki.other.example.org";
    public final static String REPO_URL = "rsync://rpki.example.org";

    @BeforeEach
    public void initObjects() {
        expirationMetrics = mock(ObjectExpirationMetrics.class);
        repositoryObjects = new RepositoryObjects(expirationMetrics);
    }

    @Test
    public void containsObjectsThatAreSet() {
        final var now = Instant.now();

        repositoryObjects.setRepositoryObject(REPO_URL, ImmutableSortedSet.of(
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(1, ChronoUnit.HOURS))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(1, ChronoUnit.DAYS)))
        ));

        assertThat(repositoryObjects.getObjects(REPO_URL)).hasSize(2);
        assertThat(repositoryObjects.getObjects(OTHER_REPO_URL)).hasSize(0);
    }

    @Test
    public void containsObjectsWithinExpirationPeriod() {
        final var now = Instant.now();

        repositoryObjects.setRepositoryObject(REPO_URL, ImmutableSortedSet.of(
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(1, ChronoUnit.HOURS))),
                RepoObject.fictionalObjectValidAtInstant(Date.from(now.plus(1, ChronoUnit.DAYS)))
        ));

        assertThat(repositoryObjects.geRepositoryObjectsAboutToExpire(OTHER_REPO_URL, 999999999)).isEmpty();

        assertThat(repositoryObjects.geRepositoryObjectsAboutToExpire(REPO_URL, 0)).isEmpty();
        assertThat(repositoryObjects.geRepositoryObjectsAboutToExpire(REPO_URL, 2)).hasSize(1);
        assertThat(repositoryObjects.geRepositoryObjectsAboutToExpire(REPO_URL, 48)).hasSize(2);
    }
}
