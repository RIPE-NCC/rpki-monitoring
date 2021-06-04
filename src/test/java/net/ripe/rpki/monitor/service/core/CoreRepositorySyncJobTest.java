package net.ripe.rpki.monitor.service.core;

import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import net.ripe.rpki.monitor.service.core.dto.PublishedObjectEntry;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CoreRepositorySyncJobTest {
    private final CoreClient coreClientStub = mock(CoreClient.class);
    private final RepositoriesState state = RepositoriesState.init(List.of(
            Triple.of("core", "https://ba-apps.ripe.net/certification/", RepositoryTracker.Type.CORE)
    ));

    @Test
    public void test_update_state_of_core() throws Exception {
        var object = PublishedObjectEntry.builder()
                .sha256("51f83dd6a174889f29038686aca85c44b64b87a4f1ab702c5b531b3c5687fc0e")
                .uri("rsync://rpki.ripe.net/repository/DEFAULT/xyz.cer")
                .build();
        when(coreClientStub.publishedObjects()).thenReturn(List.of(object));

        var subject = new CoreRepositorySyncJob(state, coreClientStub);
        subject.execute(mock(JobExecutionContext.class, RETURNS_DEEP_STUBS));

        assertThat(state.getTrackerByTag("core").get().size(Instant.now())).isEqualTo(1);
    }
}
