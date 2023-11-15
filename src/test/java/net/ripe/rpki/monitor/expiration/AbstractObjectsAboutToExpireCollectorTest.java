package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ImmutableMap;
import io.micrometer.tracing.Tracer;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotNotModifiedException;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.ripe.rpki.monitor.expiration.ObjectAndDateCollector.ObjectStatus.*;
import static net.ripe.rpki.monitor.util.RrdpSampleContentUtil.rpkiObjects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AbstractObjectsAboutToExpireCollectorTest {
    public static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM dd HH:mm:ss zzz yyyy")
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .toFormatter()
            .withLocale(Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final RepositoriesState state = RepositoriesState.init(List.of(Triple.of("rrdp", "https://rrdp.ripe.net", RepositoryTracker.Type.RRDP)), Duration.ZERO);

    ObjectAndDateCollector collector = new ObjectAndDateCollector(
            new NoopRepoFetcher("noop", "https://rrdp.ripe.net"),
            mock(CollectorUpdateMetrics.class),
            state,
            (objects) -> {},
            Tracer.NOOP,
            false
    );

    @Test
    public void itShouldCalculateMinimalExpirationSummary() throws ParseException {
        var objects = rpkiObjects("rrdp-content/apnic/notification.xml", "rrdp-content/apnic/snapshot.xml");

        var passed = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unknown = new AtomicInteger();
        var maxObjectSize = new AtomicInteger();

        var res = collector.calculateExpirationSummary(passed, rejected, unknown, maxObjectSize, objects).toList();

        assertThat(passed.get() + rejected.get() + unknown.get()).isEqualTo(objects.size());
        assertThat(maxObjectSize.get()).isGreaterThan(10_240).isLessThan(4_096_000);

        assertThat(res).hasSize(objects.size());

        // summary has distinct URIs and all URIs from input are present in output.
        var summaryUris = res.stream().map(x -> x.getUri()).collect(Collectors.toSet());
        assertThat(summaryUris).hasSize(objects.size());
        assertThat(summaryUris).allSatisfy(uri -> assertThat(objects.containsKey(uri)));

        // for all objects, the expiration is after creation
        // time window is implementation dependent
        assertThat(res).allSatisfy(x -> assertThat(x.expiration()).isAfter(x.creation()));

        // we have various types of objects and each type has 100 objects or more
        var countByExtension = res.stream().map(x -> {
            var tokens = x.uri().split("\\.");
            return tokens[tokens.length-1];
        }).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        // Will need to change when we refresh the data and tak/aspa/gbr are added
        assertThat(countByExtension.keySet()).containsExactlyInAnyOrder("cer", "mft", "roa", "crl");
        assertThat(countByExtension).allSatisfy((_, count) -> assertThat(count).isGreaterThan(100));

        // more than 10% of objects is valid for more than a month (effectively: certificates)
        assertThat(res).filteredOn(x -> x.expiration().isAfter(Instant.now().plus(Duration.ofDays(30))))
                // more than 10%
                .hasSizeGreaterThan(objects.size() / 10)
                // but less than 50% (because every cert has a manifest and we assume there is at least one .roa)
                .hasSizeLessThan((int)(0.5*objects.size()));
    }

    @Test
    public void itShouldGetCerNotAfterDate() throws ParseException {
        final String cer = "MIIFazCCBFOgAwIBAgIFW+aFOG8wDQYJKoZIhvcNAQELBQAwMzExMC8GA1UEAxMoMmE5NGE4ZGQ1NTRhZTcwMTA3MjA5OWM3MGI2NDA3NTU1ZGRkZTY2OTAeFw0yMDAyMjQxNDU2MzlaFw0yMTA3MDEwMDAwMDBaMDMxMTAvBgNVBAMTKDYwOGNiMjhhNDVhZjgxZmQ5ZDBlMDI4NWJkMjlkNTM5MTY1YjVhOWEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCtG1f7qhk7bqzrAeXKUjvPx/CcKSk6lpUB3w6EXipam0qF2ugZOsY41tC5qnF5lW+6n/RAasr3itbuaxU05uHkCSPLnnG7U2SfLty7WNLubLwFUK7EqTBHBK8T6VJ4tMhOy6vEDLJFWzZzd7EEy9R5gy39FKU+KERuzgrpkFo8lsUIVsh9BczmgmG3WqMMDGJIq1raPPYgSaNF9BAfu9lNZFEESxNtVhxFMBdq30U7+cHo4awWDk6qU9MOtdsYIpmM49XuDAoGlOXNQuqdZ07Z/eN5QXM+SWqNrjoH/qJ89E+wkNwrbO6IWEAGciHenC/CMUzahrmmZg7jdSpW5PsHAgMBAAGjggKEMIICgDAdBgNVHQ4EFgQUYIyyikWvgf2dDgKFvSnVORZbWpowHwYDVR0jBBgwFoAUKpSo3VVK5wEHIJnHC2QHVV3d5mkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwYAYIKwYBBQUHAQEEVDBSMFAGCCsGAQUFBzAChkRyc3luYzovL3Jwa2kucmlwZS5uZXQvcmVwb3NpdG9yeS9hY2EvS3BTbzNWVks1d0VISUpuSEMyUUhWVjNkNW1rLmNlcjCCASMGCCsGAQUFBwELBIIBFTCCAREwXQYIKwYBBQUHMAWGUXJzeW5jOi8vcnBraS5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvMjAvMzM0OTc1LTEzZGItNDdjZC04YzRhLTcxM2VmNzhiZWQwZC8xLzB8BggrBgEFBQcwCoZwcnN5bmM6Ly9ycGtpLnJpcGUubmV0L3JlcG9zaXRvcnkvREVGQVVMVC8yMC8zMzQ5NzUtMTNkYi00N2NkLThjNGEtNzEzZWY3OGJlZDBkLzEvWUl5eWlrV3ZnZjJkRGdLRnZTblZPUlpiV3BvLm1mdDAyBggrBgEFBQcwDYYmaHR0cHM6Ly9ycmRwLnJpcGUubmV0L25vdGlmaWNhdGlvbi54bWwwWQYDVR0fBFIwUDBOoEygSoZIcnN5bmM6Ly9ycGtpLnJpcGUubmV0L3JlcG9zaXRvcnkvREVGQVVMVC9LcFNvM1ZWSzV3RUhJSm5IQzJRSFZWM2Q1bWsuY3JsMBgGA1UdIAEB/wQOMAwwCgYIKwYBBQUHDgIwHwYIKwYBBQUHAQcBAf8EEDAOMAwEAgABMAYDBALCJ6QwDQYJKoZIhvcNAQELBQADggEBACFosm4qQ1nBvEk5OwW8scL1egXWB5dqOl0SDhHN2uIuEo8owKD7ddyeAYcg8370r5mnAmM9/JiSpNf+AjYpbWaVllJvQ6YJqbhMY1B7d5CKLewpTS80BgArxK8VugshGy+Ddffx1dS+hTRvvxURNOCgYmrHsTfgi0A2myHip8BisFUK4NqPZOyNdg1thyp++xcTs09svJbN3ZZAFMUYndU5O1GcIId8do8RM66dGan31Knn35Enj5KlQ3Q7CE+ZBd5sd4Y77c+WLtKBrtxcm7R/LbhrpotvIR0SX4/uogrtI1/UgKaYmlligc1E6IGHLQuTAocpRmlk7rUcI8TjERM=";

        final var res = collector.getDateFor("A.cer", Base64.getDecoder().decode(cer));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Mon Feb 24 14:56:39 UTC 2020"),
                DATE_FORMAT.parse("Thu Jul 01 02:00:00 CEST 2021")
        ));
    }

    @Test
    public void itShouldGetAspaNotValidAfterDate() throws ParseException {
        final String aspa = "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";

        final var res = collector.getDateFor("A.asa", Base64.getDecoder().decode(aspa));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Mon Oct 23 12:25:41 CEST 2023"),
                DATE_FORMAT.parse("Mon Oct 21 12:30:41 CEST 2024")
        ));
    }

    @Test
    void itShouldAcceptAspaV1IfRequired() throws ParseException {
        var aspaProfile13 = "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";
        var aspaProfile13Validity = ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Thu Nov 11 11:14:00 UTC 2021"),
                DATE_FORMAT.parse("Thu Nov 10 11:19:00 UTC 2022")
        );

        // By default, the version mismatch is rejected
        final var rejectedByNonAccepting = collector.getDateFor("A.asa", Base64.getDecoder().decode(aspaProfile13));
        assertThat(rejectedByNonAccepting.getLeft()).isEqualTo(REJECTED);
        assertThat(rejectedByNonAccepting.getRight()).hasValue(aspaProfile13Validity);

        ObjectAndDateCollector acceptingCollector = new ObjectAndDateCollector(
                new NoopRepoFetcher("noop", "https://rrdp.ripe.net"),
                mock(CollectorUpdateMetrics.class),
                state,
                (objects) -> {},
                Tracer.NOOP,
                true
        );

        // But the accepting collector accepts it
        final var res = acceptingCollector.getDateFor("A.asa", Base64.getDecoder().decode(aspaProfile13));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(aspaProfile13Validity);
    }

    @Test
    public void itShouldGetRoaNotValidAfterDate() throws ParseException {
        final String roa = "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";

        final var res = collector.getDateFor("A.roa", Base64.getDecoder().decode(roa));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Mon Feb 24 17:53:22 UTC 2020"),
                DATE_FORMAT.parse("Thu Jul 01 02:00:00 CEST 2021")
        ));
    }

    @Test
    public void itShouldGetCrlNextUpdateDate() throws ParseException {
        final  String crl = "MIIBxjCBrwIBATANBgkqhkiG9w0BAQsFADAzMTEwLwYDVQQDEyg5MDI1MWVkN2UwODFhNWM2NDQ4MDFjYWExYmFkMGMzMTE4MTQ5YWZkFw0xOTEwMjkwODQwMTdaFw0xOTEwMzAwODQwMTdaMBYwFAIDGs0KFw0xOTEwMDIxNDIwMzJaoDAwLjAfBgNVHSMEGDAWgBSQJR7X4IGlxkSAHKobrQwxGBSa/TALBgNVHRQEBAICAIEwDQYJKoZIhvcNAQELBQADggEBADFvZokQE2NGyG3x550XRzaJkux4B3r5EZu8ib77MOqrTSYsT5QqTz1CAt117Ebf0wuo4VmhGPKXDOb5OubOz1r4s5kjovIHvDeZ1/qrlzbcwwDIrVtqqEBmEEyYcip166JRrqzMKgl7tWpnPlJOA3JTu8+dYjS++i9Q2c8eAXzEZzX85K8ruGBelSnpX8lUcTSO3+AS8175Wv/WHEPZeJFbJcmhTyiNKWfebDDTX5gQn1BG239aS/HeKUNVGIEy0ROnBVym2BN9I6beegnEh4rJIrhuQSYuZ0HUS1oIHcX3OPC3jGFOMBZpktT3P6pl4gEYL5CHbz+8es+uf2m0+CI=";

        final var res = collector.getDateFor("A.crl", Base64.getDecoder().decode(crl));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Tue Oct 29 08:40:17 UTC 2019"),
                DATE_FORMAT.parse("Wed Oct 30 09:40:17 CET 2019")
        ));
    }

    @Test
    public void itShouldGetMftNextUpdateTimeDate() throws ParseException {
        final String mft = "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";

        final var res = collector.getDateFor("A.mft", Base64.getDecoder().decode(mft));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Mon Sep 16 07:44:45 CEST 2019"),
                DATE_FORMAT.parse("Tue Sep 17 07:44:45 CEST 2019")
        ));
    }

    @Test
    public void itShouldRejectAnInvalidObject() throws ParseException {
        final String crt = "DEADBEEF";

        final var res = collector.getDateFor("A.cer", Base64.getDecoder().decode(crt));

        assertThat(res.getLeft()).isEqualTo(REJECTED);
        assertThat(res.getRight()).isEmpty();
    }

    @Test
    public void itShouldReturnEmptyForUnknownObjectType() throws ParseException {
        final var res = collector.getDateFor("A.bla", new byte[]{});
        assertThat(res.getLeft()).isEqualTo(UNKNOWN);
        assertThat(res.getRight()).isEmpty();
    }
}

record NoopRepoFetcher(String name, String url) implements RepoFetcher {
    @Override
    public ImmutableMap<String, RpkiObject> fetchObjects() throws FetcherException, SnapshotNotModifiedException {
        return ImmutableMap.of();
    }

    @Override
    public Meta meta() {
        return new Meta(name, url);
    }
}
