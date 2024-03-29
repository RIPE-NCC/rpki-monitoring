package net.ripe.rpki.monitor.expiration;

import lombok.NonNull;
import lombok.Setter;
import net.ripe.rpki.monitor.expiration.fetchers.RRDPStructureException;
import net.ripe.rpki.monitor.expiration.fetchers.RepoUpdateFailedException;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.util.Sha256;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration
class RrdpObjectsAboutToExpireCollectorIntegrationTest {
    private static int testServerPort = -1;

    @DynamicPropertySource
    public static void mockRRDPEndpointProperties(DynamicPropertyRegistry registry) throws IOException {
        // Use mockserver to find a port
        var server = new MockWebServer();
        server.start();
        testServerPort = server.getPort();
        server.shutdown();

        registry.add("rrdp.targets[0].name", () -> "main");
        registry.add("rrdp.targets[0].notification-url", () -> "http://rrdp.ripe.net:" + testServerPort + "/notification.xml");
        registry.add("rrdp.targets[0].connect-to[rrdp.ripe.net]", () -> server.getHostName());
    }

    @NonNull
    private MockWebServer server;

    @Setter
    @Autowired
    private RepositoriesState repositoriesState;

    @Setter
    @Autowired
    private Collectors collectors;

    private ObjectAndDateCollector subject;


    private String getNotificationXml(String serial, String snapshotHash) {
        return """
        <notification xmlns="http://www.ripe.net/rpki/rrdp" version="1" session_id="329ee04b-72b9-4221-8fe5-f04534db304d" serial="%s">
        <snapshot uri="http://rrdp.ripe.net:%d/%s/snapshot.xml" hash="%s"/>
        </notification>
        """.formatted(serial, server.getPort(), serial, snapshotHash);
    }


    // FIXME: serial is fixed in snapshot, but making this dynamic also updates the notification payload
    private String getSnapshotXml(String serial) {
        return """
                <snapshot version="1" session_id="329ee04b-72b9-4221-8fe5-f04534db304d" serial="%s" xmlns="http://www.ripe.net/rpki/rrdp">
                    <publish uri="rsync://rpki.ripe.net/repository/DEFAULT/21/765e79-d0a3-472c-b5df-6d324c51362d/1/Q8uCW6eAw7tnl2QxNmQTf50fxAg.mft">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</publish>
                    <publish uri="rsync://rpki.ripe.net/repository/DEFAULT/d2/9a8a0c-6af3-4da9-be70-17480c46e24f/1/kCUe1-CBpcZEgByqG60MMRgUmv0.crl">MIIBxjCBrwIBATANBgkqhkiG9w0BAQsFADAzMTEwLwYDVQQDEyg5MDI1MWVkN2UwODFhNWM2NDQ4MDFjYWExYmFkMGMzMTE4MTQ5YWZkFw0xOTEwMjkwODQwMTdaFw0xOTEwMzAwODQwMTdaMBYwFAIDGs0KFw0xOTEwMDIxNDIwMzJaoDAwLjAfBgNVHSMEGDAWgBSQJR7X4IGlxkSAHKobrQwxGBSa/TALBgNVHRQEBAICAIEwDQYJKoZIhvcNAQELBQADggEBADFvZokQE2NGyG3x550XRzaJkux4B3r5EZu8ib77MOqrTSYsT5QqTz1CAt117Ebf0wuo4VmhGPKXDOb5OubOz1r4s5kjovIHvDeZ1/qrlzbcwwDIrVtqqEBmEEyYcip166JRrqzMKgl7tWpnPlJOA3JTu8+dYjS++i9Q2c8eAXzEZzX85K8ruGBelSnpX8lUcTSO3+AS8175Wv/WHEPZeJFbJcmhTyiNKWfebDDTX5gQn1BG239aS/HeKUNVGIEy0ROnBVym2BN9I6beegnEh4rJIrhuQSYuZ0HUS1oIHcX3OPC3jGFOMBZpktT3P6pl4gEYL5CHbz+8es+uf2m0+CI=</publish>
                    <publish uri="rsync://rpki.ripe.net/repository/DEFAULT/99/13fb65-4ee7-4026-aa3a-9d56e3adca90/1/IGUhqMPsmuguPLDT3XZl_hGfvS0.roa">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</publish>
                    <publish uri="rsync://rpki.ripe.net/repository/DEFAULT/YIyyikWvgf2dDgKFvSnVORZbWpo.cer">MIIFazCCBFOgAwIBAgIFW+aFOG8wDQYJKoZIhvcNAQELBQAwMzExMC8GA1UEAxMoMmE5NGE4ZGQ1NTRhZTcwMTA3MjA5OWM3MGI2NDA3NTU1ZGRkZTY2OTAeFw0yMDAyMjQxNDU2MzlaFw0yMTA3MDEwMDAwMDBaMDMxMTAvBgNVBAMTKDYwOGNiMjhhNDVhZjgxZmQ5ZDBlMDI4NWJkMjlkNTM5MTY1YjVhOWEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCtG1f7qhk7bqzrAeXKUjvPx/CcKSk6lpUB3w6EXipam0qF2ugZOsY41tC5qnF5lW+6n/RAasr3itbuaxU05uHkCSPLnnG7U2SfLty7WNLubLwFUK7EqTBHBK8T6VJ4tMhOy6vEDLJFWzZzd7EEy9R5gy39FKU+KERuzgrpkFo8lsUIVsh9BczmgmG3WqMMDGJIq1raPPYgSaNF9BAfu9lNZFEESxNtVhxFMBdq30U7+cHo4awWDk6qU9MOtdsYIpmM49XuDAoGlOXNQuqdZ07Z/eN5QXM+SWqNrjoH/qJ89E+wkNwrbO6IWEAGciHenC/CMUzahrmmZg7jdSpW5PsHAgMBAAGjggKEMIICgDAdBgNVHQ4EFgQUYIyyikWvgf2dDgKFvSnVORZbWpowHwYDVR0jBBgwFoAUKpSo3VVK5wEHIJnHC2QHVV3d5mkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwYAYIKwYBBQUHAQEEVDBSMFAGCCsGAQUFBzAChkRyc3luYzovL3Jwa2kucmlwZS5uZXQvcmVwb3NpdG9yeS9hY2EvS3BTbzNWVks1d0VISUpuSEMyUUhWVjNkNW1rLmNlcjCCASMGCCsGAQUFBwELBIIBFTCCAREwXQYIKwYBBQUHMAWGUXJzeW5jOi8vcnBraS5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvMjAvMzM0OTc1LTEzZGItNDdjZC04YzRhLTcxM2VmNzhiZWQwZC8xLzB8BggrBgEFBQcwCoZwcnN5bmM6Ly9ycGtpLnJpcGUubmV0L3JlcG9zaXRvcnkvREVGQVVMVC8yMC8zMzQ5NzUtMTNkYi00N2NkLThjNGEtNzEzZWY3OGJlZDBkLzEvWUl5eWlrV3ZnZjJkRGdLRnZTblZPUlpiV3BvLm1mdDAyBggrBgEFBQcwDYYmaHR0cHM6Ly9ycmRwLnJpcGUubmV0L25vdGlmaWNhdGlvbi54bWwwWQYDVR0fBFIwUDBOoEygSoZIcnN5bmM6Ly9ycGtpLnJpcGUubmV0L3JlcG9zaXRvcnkvREVGQVVMVC9LcFNvM1ZWSzV3RUhJSm5IQzJRSFZWM2Q1bWsuY3JsMBgGA1UdIAEB/wQOMAwwCgYIKwYBBQUHDgIwHwYIKwYBBQUHAQcBAf8EEDAOMAwEAgABMAYDBALCJ6QwDQYJKoZIhvcNAQELBQADggEBACFosm4qQ1nBvEk5OwW8scL1egXWB5dqOl0SDhHN2uIuEo8owKD7ddyeAYcg8370r5mnAmM9/JiSpNf+AjYpbWaVllJvQ6YJqbhMY1B7d5CKLewpTS80BgArxK8VugshGy+Ddffx1dS+hTRvvxURNOCgYmrHsTfgi0A2myHip8BisFUK4NqPZOyNdg1thyp++xcTs09svJbN3ZZAFMUYndU5O1GcIId8do8RM66dGan31Knn35Enj5KlQ3Q7CE+ZBd5sd4Y77c+WLtKBrtxcm7R/LbhrpotvIR0SX4/uogrtI1/UgKaYmlligc1E6IGHLQuTAocpRmlk7rUcI8TjERM=</publish>
                </snapshot>
                """.formatted(serial);
    }


    @BeforeEach
    public void setUp() throws IOException {
        subject = collectors.getRrdpCollectors().get(0);

        server = new MockWebServer();
        server.start(testServerPort);
    }
    @AfterEach
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueXMLResponse(@NonNull String payload) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setResponseCode(200)
                .setBody(payload)
        );
    }

    @Test
    void itShouldUpdateRrdpRepositoryState() throws Exception {
        final String serial = "574";

        String snapshotXml = getSnapshotXml(serial);

        enqueueXMLResponse(getNotificationXml(serial, Sha256.asString(snapshotXml)));
        enqueueXMLResponse(snapshotXml);

        subject.run();

        var tracker = repositoriesState.getTrackerByTag("main").get();
        assertThat(tracker.view(Instant.now()).size()).isEqualTo(4);

        //  And it should request notification, followed by snapshot
        assertThat(server.takeRequest().getRequestUrl().pathSegments()).endsWith("notification.xml");
        assertThat(server.takeRequest().getRequestUrl().pathSegments()).endsWith("snapshot.xml");
    }

    @Test
    void itShouldCheckHash() {

        final String serial = "666";
        var snapshotXml = getSnapshotXml(serial);
        enqueueXMLResponse(getNotificationXml(serial, "ababababababababababababababababababab1b1b1b1bababababababababab"));
        enqueueXMLResponse(snapshotXml);

        // Exception has both hashes in message
        assertThatThrownBy(subject::run)
                .isExactlyInstanceOf(RRDPStructureException.class)
                .hasMessageContaining(Sha256.asString(snapshotXml))
                .hasMessageContaining("ababababababababababababababababababab1b1b1b1bababababababababab");
    }

    @Test
    void itShouldOnlyDownloadSnapshotWhenNeeded() throws Exception {
        final String serial = "42";
        var snapshotXml = getSnapshotXml(serial);
        enqueueXMLResponse(getNotificationXml(serial, Sha256.asString(snapshotXml)));
        enqueueXMLResponse(snapshotXml);
        // No response will be served if notification is not present
        enqueueXMLResponse(getNotificationXml(serial, Sha256.asString(snapshotXml)));

        subject.run();


        subject.run();

        assertThat(server.takeRequest().getRequestUrl().encodedPathSegments()).endsWith("notification.xml");
        assertThat(server.takeRequest().getRequestUrl().encodedPathSegments()).endsWith("snapshot.xml");
        assertThat(server.takeRequest().getRequestUrl().encodedPathSegments()).endsWith("notification.xml");
    }

    @Test
    void itShouldDownloadSnapshotWhenChanged() throws Exception {
        // unrestricted in spec, but use 2**31 - 1 because we use ints in the test code.
        var serial = new Random().nextInt(1, 2147483647);
        var snapshotXml = getSnapshotXml(String.valueOf(serial));

        // Correct serial
        enqueueXMLResponse(getNotificationXml(String.valueOf(serial), Sha256.asString(snapshotXml)));
        enqueueXMLResponse(snapshotXml);

        subject.run();

        // Serial mismatch
        enqueueXMLResponse(getNotificationXml(String.valueOf(serial + 1), Sha256.asString(snapshotXml)));
        enqueueXMLResponse(snapshotXml);

        assertThatThrownBy(() -> subject.run())
                .isExactlyInstanceOf(RRDPStructureException.class)
                .hasMessageContaining(String.valueOf(serial));

        // Snapshot is not a snapshot but a notification.xml fille
        var unrelatedNotificationXml = getNotificationXml(String.valueOf(serial + 2), "DEADBEEFINVALID");
        enqueueXMLResponse(getNotificationXml(String.valueOf(serial + 2), Sha256.asString(unrelatedNotificationXml)));
        enqueueXMLResponse(unrelatedNotificationXml);

        assertThatThrownBy(() -> subject.run())
                .isExactlyInstanceOf(RRDPStructureException.class);
    }

    @Test
    void itShouldRaiseOn404_notification() throws Exception {
        // A 404 for the notification file
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setResponseCode(404)
                .setBody("404 - File not found")
        );

        assertThatThrownBy(() -> subject.run())
                .asInstanceOf(InstanceOfAssertFactories.type(RepoUpdateFailedException.class))
                .matches(e -> HttpStatusCode.valueOf(404).equals(((RrdpHttp.HttpResponseException)e.getCause()).getStatusCode()));
    }

    @Test
    void itShouldRaiseOn404_snapshot() throws Exception {
        // unrestricted in spec, but use 2**31 - 1 because we use ints in the test code.
        var serial = new Random().nextInt(1, 2147483647);
        var snapshotXml = getSnapshotXml(String.valueOf(serial));

        enqueueXMLResponse(getNotificationXml(String.valueOf(serial), Sha256.asString(snapshotXml)));
        // A 404 for the snaphot
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setResponseCode(404)
                .setBody("404 - File not found")
        );

        assertThatThrownBy(() -> subject.run())
                .asInstanceOf(InstanceOfAssertFactories.type(RepoUpdateFailedException.class))
                .matches(e -> HttpStatusCode.valueOf(404).equals(((RrdpHttp.HttpResponseException)e.getCause()).getStatusCode()));
    }
}