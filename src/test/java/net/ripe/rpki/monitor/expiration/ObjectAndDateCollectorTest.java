package net.ripe.rpki.monitor.expiration;

import io.micrometer.tracing.Tracer;
import net.ripe.rpki.monitor.config.AppConfig;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static net.ripe.rpki.monitor.expiration.ObjectAndDateCollector.ObjectStatus.ACCEPTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObjectAndDateCollectorTest {

    private final RepositoriesState state = RepositoriesState.init(
            List.of(Triple.of("rrdp", "https://rrdp.ripe.net", RepositoryTracker.Type.RRDP)),
            Duration.ZERO
    );

    private final ObjectAndDateCollector collector = new ObjectAndDateCollector(
            new AbstractObjectsAboutToExpireCollectorTest.NoopRepoFetcher("noop", "https://rrdp.ripe.net"),
            mock(CollectorUpdateMetrics.class),
            state,
            (objects) -> {},
            Tracer.NOOP,
            new AppConfig()
    );

    @Test
    void getDateFor_roaIsAcceptedWithCorrectValidityPeriod() {
        var bytes = ("MIIHCgYJKoZIhvcNAQcCoIIG+zCCBvcCAQMxDTALBglghkgBZQMEAgEwLQYLKoZIhvcNAQkQARigHgQcMBoCAwJPKjATMB" +
                "EEAgABMAswCQMEApbxzAIBGKCCBQQwggUAMIID6KADAgECAhQ/jJRK4ErvJJSlbJ2dWFj8Lmqy1jANBgkqhkiG9w0BAQsFADAzMTEw" +
                "LwYDVQQDEyg0ZmMzMzZiZjlmM2RlNWNlNDE0MTRiZDE5NzE5NDVmNGIyNDZiZmNjMB4XDTI2MDExMzE1NTAzM1oXDTI3MDExMjE1NTU" +
                "zM1owMzExMC8GA1UEAxMoQTAzNUE3Nzg2NzgxMjI0M0QzRjY4QkZGNTBCQzNFMzQxRTJEQzE5OTCCASIwDQYJKoZIhvcNAQEBBQADggE" +
                "PADCCAQoCggEBAMQKGP0gIizTFRGLww7VZRoKU1FXtg07tOzvPsltfj4PHIYtaBwvK69x2bkshZYlhiL5zF0fjRDf0TsW5VZMeYtRBySb" +
                "YvwmOBO8A1Yd/eWcjfAgEVyHTomaSo4kvik/UjfixHXDjjwSyuMdLESP8nEyD9C8lfEwoACq03ggRLu45K12dJ9gqU8KNlqr7XQ4Be" +
                "iRgLq2O0mJoA1NlzWzn6PpBQRGv+Ln4WxxikZflINwhNwkUJXuLKarhDILRrjhVmFX0vEJHJnrvX4aRPatPDjkWlQ/EfP24u5yaEa8" +
                "ysUJCT1U8JXAhsOK/9fYyS/zETaAEySwzrYNoO+nG7z564cCAwEAAaOCAgowggIGMB0GA1UdDgQWBBSgNad4Z4EiQ9P2i/9QvD40H" +
                "i3BmTAfBgNVHSMEGDAWgBRPwza/nz3lzkFBS9GXGUX0ska/zDAOBgNVHQ8BAf8EBAMCB4AwgZUGA1UdHwSBjTCBijCBh6CBhKCBgYZ" +
                "/cnN5bmM6Ly9yc3luYy5wYWFzLnJwa2kucmlwZS5uZXQvcmVwb3NpdG9yeS8wOWJlM2FhZS1hZWExLTQxZGMtYjFiOS05NWFjNTkxO" +
                "DI0NGQvMC80RkMzMzZCRjlGM0RFNUNFNDE0MTRCRDE5NzE5NDVGNEIyNDZCRkNDLmNybDBkBggrBgEFBQcBAQRYMFYwVAYIKwYBBQU" +
                "HMAKGSHJzeW5jOi8vcnBraS5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvVDhNMnY1ODk1YzVCUVV2Umx4bEY5TEpHdjh3LmNlc" +
                "jB7BggrBgEFBQcBCwRvMG0wawYIKwYBBQUHMAuGX3JzeW5jOi8vcnN5bmMucGFhcy5ycGtpLnJpcGUubmV0L3JlcG9zaXRvcnkvMDl" +
                "iZTNhYWUtYWVhMS00MWRjLWIxYjktOTVhYzU5MTgyNDRkLzAvQVMxNTEzMzgucm9hMBgGA1UdIAEB/wQOMAwwCgYIKwYBBQUHDgIwH" +
                "wYIKwYBBQUHAQcBAf8EEDAOMAwEAgABMAYDBAKW8cwwDQYJKoZIhvcNAQELBQADggEBAIbvhMYZ6dmK+WsMT3Q9XWxLjdd1RG/L+JI" +
                "n8AAWajBztDmd25v9735kpO502LVHPlqAtCC/QlysuVZc6wCQWSwxv/IWMtMWiJdHJm+SJEyDm5U4atYsPyrL+XwZnMSmG2i+B0e2" +
                "RfE+IpM/gjLiY8/StIcnOlKcZi2ExbtgprUoXvIqvuUS0Ipn1ObxXBVcdaqPoqHOSUbAjEZMJemNm/TwpdUDoSPgV17Lnnslr5xF2l" +
                "RiI/lHagS7cJAmfyYEWmUgy57bw8CwZ3Ao+WVIO03ExWQXsEgrEW52iMp6j+U8PljdEtQSJ7EJstBdKIHc+9n0jRCmbZVubqKmfKSj" +
                "yigxggGqMIIBpgIBA4AUoDWneGeBIkPT9ov/ULw+NB4twZkwCwYJYIZIAWUDBAIBoGswGgYJKoZIhvcNAQkDMQ0GCyqGSIb3DQEJEAE" +
                "YMBwGCSqGSIb3DQEJBTEPFw0yNjAxMTMxNTU1MzNaMC8GCSqGSIb3DQEJBDEiBCA3GfOUyPyOmImPCRB12KGTp540lWfZ3wdLzBe6xD" +
                "65BjANBgkqhkiG9w0BAQEFAASCAQAFYY7E51BNmbebKp0FW2xzEQatb6Mc3DLIlCutjz0fbiV+vlbWmzFVuOmzbAXBN6MzYSYSP1d8+" +
                "aCxE9G6s9PxjeNkDfuS9p9g0Hz9h6tp8UPQNdDcD1KRmKEtIJkelibLkjObHdv4YqEEADAMZNbM0HpD8xkDqgIjvNDb/Dk/VKec1ef" +
                "pKPs+NaDbLr/6l6ajqs2ZceRcSzhPrnPu1reHVFaitI1GB50Q65XwBpQEpqCcFOcIqer8kPj7OrjCqANQhb93e03+BUW9bYOjA2e4WX" +
                "Yymtgpy7h4nc6YAJSHL7GHAerSN6Q9K5Js94hfH4XeAX5b/PSWXzHkPt7ieD4l").trim();

        var result = collector.getDateFor("AS151338.roa",
                Base64.getDecoder().decode(bytes));

        assertThat(result.getLeft()).isEqualTo(ACCEPTED);
        assertThat(result.getRight()).hasValueSatisfying(period -> {
            assertThat(period.creation()).isEqualTo(Instant.parse("2026-01-13T15:50:33Z"));
            assertThat(period.expiration()).isEqualTo(Instant.parse("2027-01-12T15:55:33Z"));
        });
    }
}