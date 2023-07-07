package net.ripe.rpki.monitor.fetchers;

import net.ripe.rpki.monitor.expiration.fetchers.*;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RrdpSnapshotClientTest {
    public static final String EXAMPLE_ORG_NOTIFICATION_XML = "https://example.org/notification.xml";
    @Mock
    private RrdpHttp http;

    private RrdpSnapshotClient subject;

    @BeforeEach
    void setUp() {
        subject = new RrdpSnapshotClient(http);
    }

    @Test
    void loadNotificationAndSnapshot() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, RepoUpdateAbortedException, SnapshotNotModifiedException {
        when(http.fetch(any())).thenReturn(
            new ClassPathResource("rrdp/ripe-notification.xml").getInputStream().readAllBytes(),
            new ClassPathResource("rrdp/ripe-snapshot.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        var res = subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty());
        assertThat(res.serialAsLong()).isEqualTo(1742L);
        assertThat(res.collisionCount()).isEqualTo(0);
        assertThat(res.snapshotUrl()).isEqualTo("https://rrdp.ripe.net/a2d845c4-5b91-4015-a2b7-988c03ce232a/1742/snapshot.xml");
    }

    @Test
    void loadNotificationAndSnapshot_not_changed() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, RepoUpdateAbortedException, SnapshotNotModifiedException {
        when(http.fetch(any())).thenReturn(
                new ClassPathResource("rrdp/ripe-notification.xml").getInputStream().readAllBytes(),
                new ClassPathResource("rrdp/ripe-snapshot.xml").getInputStream().readAllBytes(),
                new ClassPathResource("rrdp/ripe-notification.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        var res = subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty());

        assertThatThrownBy(() -> subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.of(res)))
                .asInstanceOf(InstanceOfAssertFactories.throwable(SnapshotNotModifiedException.class));
    }

    @Test
    void loadNotificationAndSnapshot_collisions() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, RepoUpdateAbortedException, SnapshotNotModifiedException {
        when(http.fetch(any())).thenReturn(
            new ClassPathResource("rrdp/ripe-notification-collision.xml").getInputStream().readAllBytes(),
            new ClassPathResource("rrdp/ripe-snapshot-collision.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        var res = subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty());
        assertThat(res.collisionCount()).isEqualTo(1);
    }

    void detectsHashMismatch() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException {
        when(http.fetch(any())).thenReturn(
            new ClassPathResource("rrdp/ripe-notification.xml").getInputStream().readAllBytes(),
            new ClassPathResource("rrdp/ripe-snapshot-collision.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        assertThatThrownBy(() -> subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty()))
                .asInstanceOf(InstanceOfAssertFactories.throwable(RepoUpdateFailedException.class));
    }

    @Test
    void detectsSerialMismatch() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException {
        when(http.fetch(any())).thenReturn(
            new ClassPathResource("rrdp/ripe-notification-serial-2.xml").getInputStream().readAllBytes(),
            new ClassPathResource("rrdp/ripe-snapshot.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        assertThatThrownBy(() -> subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty()))
                .asInstanceOf(InstanceOfAssertFactories.throwable(RRDPStructureException.class));
    }

    @Test
    void detectsSessionMismatch() throws RrdpHttp.HttpResponseException, RrdpHttp.HttpTimeout, IOException, RRDPStructureException, RepoUpdateAbortedException, SnapshotNotModifiedException {
        // Return the same snapshot for different session
        when(http.fetch(any())).thenReturn(
            new ClassPathResource("rrdp/ripe-notification-session-mismatch.xml").getInputStream().readAllBytes(),
            new ClassPathResource("rrdp/ripe-snapshot-session-mismatch.xml").getInputStream().readAllBytes()
        );
        when(http.overrideHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        assertThatThrownBy(() -> subject.fetchObjects(EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty()))
                .asInstanceOf(InstanceOfAssertFactories.throwable(RRDPStructureException.class));
    }
}
