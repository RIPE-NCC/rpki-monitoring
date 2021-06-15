package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RepoFetcher;
import net.ripe.rpki.monitor.expiration.fetchers.SnapshotNotModifiedException;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryTracker;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static net.ripe.rpki.monitor.expiration.ObjectAndDateCollector.ObjectStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AbstractObjectsAboutToExpireCollectorTest {
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");

    private final RepositoriesState state = RepositoriesState.init(List.of(Triple.of("rrdp", "https://rrdp.ripe.net", RepositoryTracker.Type.RRDP)));

    ObjectAndDateCollector collector = new ObjectAndDateCollector(
            new NoopRepoFetcher("https://rrdp.ripe.net"),
            mock(CollectorUpdateMetrics.class),
            state
    );

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
        final String mft = "MIAGCSqGSIb3DQEHAqCAMIACAQMxDzANBglghkgBZQMEAgEFADCABgsqhkiG9w0BCRABGqCAJIAEgcIwgb8CASMYDzIwMTkwOTE2MDU0NDQ1WhgPMjAxOTA5MTcwNTQ0NDVaBglghkgBZQMEAgEwgYwwRBYfRURKTnVrVGZScnJkY210SUlYb3pid2doYXdNLnJvYQMhAAFkv5qsQqUubHX7+mFc3KB3vx8eENh5g63BPFApm6B5MEQWH1E4dUNXNmVBdzd0bmwyUXhObVFUZjUwZnhBZy5jcmwDIQAyI/QdXoKfB+rJRlr1SxY3tOCIwitXOkwAZHZAq84J4gAAAAAAAKCAMIIFCDCCA/CgAwIBAgIEEdl1ZDANBgkqhkiG9w0BAQsFADAzMTEwLwYDVQQDEyg0M2NiODI1YmE3ODBjM2JiNjc5NzY0MzEzNjY0MTM3ZjlkMWZjNDA4MB4XDTE5MDkxNjA1Mzk0NVoXDTE5MDkyMzA1NDQ0NVowMzExMC8GA1UEAxMoNDg1MmMwYzAzNzYxZjUwMGY3ZmNlZTRjYzYzNmM1OTY5MzI2ODlkYjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALOQqkBnxmfxixGDB78evzzPPTIOo8EVZrhg2Nv/2qG8C6Ra//wkiVMDegBeT+3I8Yu1A956g9SVor6AhskvJj5hMuGKynprAKOsjwVo9WImz2Lbx1WFPkWDxiEJiezr93QqzV2pdRiFw9w7OvUw/mEtQw7WYgyyuIvgNvne23Phnhm2vgpkLjuARZVTtTdvKDviB115Hh9Hw2FWqPToma+Su8BvLENq8652QLO7gq+bVKs8ElFgq51fKxUswMQeNnjiDQ7v3TW5crhb9nUW3UyGbm2Wzre8qntBfERzhXOzpjrTzaP9Zwuq5SahFODjnS59SxUheYwiwQtpELFyJtUCAwEAAaOCAiIwggIeMB0GA1UdDgQWBBRIUsDAN2H1APf87kzGNsWWkyaJ2zAfBgNVHSMEGDAWgBRDy4Jbp4DDu2eXZDE2ZBN/nR/ECDAOBgNVHQ8BAf8EBAMCB4AwZAYIKwYBBQUHAQEEWDBWMFQGCCsGAQUFBzAChkhyc3luYzovL3Jwa2kucmlwZS5uZXQvcmVwb3NpdG9yeS9ERUZBVUxUL1E4dUNXNmVBdzd0bmwyUXhObVFUZjUwZnhBZy5jZXIwgY0GCCsGAQUFBwELBIGAMH4wfAYIKwYBBQUHMAuGcHJzeW5jOi8vcnBraS5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvMjEvNzY1ZTc5LWQwYTMtNDcyYy1iNWRmLTZkMzI0YzUxMzYyZC8xL1E4dUNXNmVBdzd0bmwyUXhObVFUZjUwZnhBZy5tZnQwgYEGA1UdHwR6MHgwdqB0oHKGcHJzeW5jOi8vcnBraS5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvMjEvNzY1ZTc5LWQwYTMtNDcyYy1iNWRmLTZkMzI0YzUxMzYyZC8xL1E4dUNXNmVBdzd0bmwyUXhObVFUZjUwZnhBZy5jcmwwGAYDVR0gAQH/BA4wDDAKBggrBgEFBQcOAjAhBggrBgEFBQcBBwEB/wQSMBAwBgQCAAEFADAGBAIAAgUAMBUGCCsGAQUFBwEIAQH/BAYwBKACBQAwDQYJKoZIhvcNAQELBQADggEBADDTJlV3AhMrM9TF3GRL/p4Xx+XnGirNz8zxEaKBL7Bw4cRFraKYC2RHkYG2A5fl93j6VxOguJl//ukW9fOYHCHzU3X0ZZy+B/p11SWK/iNFO718uGyQMrxNso5Vu6orFvdBdyc9GpRe3kfvnK/V4iJiHZhWFxIbLMB4DFWDyUsjAGoD+WwBiX9ERRjNJXcTJ81kU7zCa6LxAVfw0DdejiCwXQA38rXmcFfzaOmDDpruB+QZx/25gB2vX+0lupGV6tLlOSAxVXM5bXjNQZEcfzQQKt2nR6aOASaR6HbKTOcTHCLz4VwTXGMxepc35xaJVNTx3yQRWA3et6rGxyun9qAAADGCAawwggGoAgEDgBRIUsDAN2H1APf87kzGNsWWkyaJ2zANBglghkgBZQMEAgEFAKBrMBoGCSqGSIb3DQEJAzENBgsqhkiG9w0BCRABGjAcBgkqhkiG9w0BCQUxDxcNMTkwOTE2MDUzOTQ1WjAvBgkqhkiG9w0BCQQxIgQgI0NGgRPt3hhvPqJKstnc4P1B4HQwoDk8tggtiVnsOYEwDQYJKoZIhvcNAQELBQAEggEAFbdAU9EO0ec7XQGj3d2RSJQBAkQ53Z9HFnA6svVVeVkmN8gfVScMtw6eiakmCwsp/pjRNKNB9crU7gBBFo1E/AOJtO33Tf/ubKm8KOFl6a7DXd2zKJ4icErd3pDOAtMQaP856IPkKzbf0+A2qGJddMKguKvjbjkzax7Hl7BQOPNPwFHSwnsjgF/vTus+AP+Z2PXA9TxGNPzbUVh48WGJYqfqsLjDkeva0x5UvB1Ypyr4y/gvE641IZVEfyTmw9233ZsonlTz66KO1LfAkaUR0ooM+5FPQUPAJUnlR09pHQq0Viq3hNwEzELJUjMu+zFwDcY6TQodBRUFYSuFpe6q0AAAAAAAAA==";

        final var res = collector.getDateFor("A.mft", Base64.getDecoder().decode(mft));
        assertThat(res.getLeft()).isEqualTo(ACCEPTED);
        assertThat(res.getRight()).hasValue(ObjectAndDateCollector.ObjectValidityPeriod.of(
                DATE_FORMAT.parse("Mon Sep 16 07:44:45 CEST 2019"),
                DATE_FORMAT.parse("Mon Sep 17 07:44:45 CEST 2019")
        ));
    }

    @Test
    public void itShouldRejectAnInvalidObject() throws ParseException {
        final String crt = "MIIFvDCCBKSgAwIBAgIGAIJu9HJwMA0GCSqGSIb3DQEBCwUAMDMxMTAvBgNVBAMTKDJhOTRhOGRkNTU0YWU3MDEwNzIwOTljNzBiNjQwNzU1NWRkZGU2NjkwHhcNMjAxMjE1MDkwODQ0WhcNMjEwNzAxMDAwMDAwWjAzMTEwLwYDVQQDEyhmODNmYWVjOTNkNDAzZjM3MTM4MjNmYTM5YzdkMjdjNjJlOTIxNDcxMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA/IHmyx/JqwAWoeaitfODZbb0uh0hGviWbO7glD2m7xu3uAoxoccR7Zn2n4Fq/aN4fhMRdaYU10+If/j+llF7rXnZ/+Ux9soqBG44RWxohkOyn40RXW2Cn7+IACIRLGr1BUcvZczFMaW1X5jLz15b40yqImDC3u2tlMegsPKwkeE3Th9855i8gTajkOUYw9+xGDbmeavE+QovSh73nhAGkCsXITv+EPcM+14ZjakaeO/Wz2DXYQe278GBDhLvURS00Kf1fFLQrYE46SuMcuTy0MfSNox3QOPmQ9veOqQK1kdK8cFgZI7yLnkzyOJ4i3y1YpzbpxZkCd8NCRdD3WtVrQIDAQABo4IC1DCCAtAwHQYDVR0OBBYEFPg/rsk9QD83E4I/o5x9J8YukhRxMB8GA1UdIwQYMBaAFCqUqN1VSucBByCZxwtkB1Vd3eZpMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMGgGCCsGAQUFBwEBBFwwWjBYBggrBgEFBQcwAoZMcnN5bmM6Ly9ycGtpLnByZXBkZXYucmlwZS5uZXQvcmVwb3NpdG9yeS9hY2EvS3BTbzNWVks1d0VISUpuSEMyUUhWVjNkNW1rLmNlcjCCAUYGCCsGAQUFBwELBIIBODCCATQwZQYIKwYBBQUHMAWGWXJzeW5jOi8vcnBraS5wcmVwZGV2LnJpcGUubmV0L3JlcG9zaXRvcnkvREVGQVVMVC9iOS85NzBjZjAtOTI5OC00ZmYwLWJhZjUtMjEwYWUwNTY5YTQ1LzEvMIGFBggrBgEFBQcwCoZ5cnN5bmM6Ly9ycGtpLnByZXBkZXYucmlwZS5uZXQvcmVwb3NpdG9yeS9ERUZBVUxUL2I5Lzk3MGNmMC05Mjk4LTRmZjAtYmFmNS0yMTBhZTA1NjlhNDUvMS8xLUQtdXlUMUFQemNUZ2otam5IMG54aTZTRkhFLm1mdDBDBggrBgEFBQcwDYY3aHR0cDovL3B1Yi1zZXJ2ZXIuZWxhc3RpY2JlYW5zdGFsay5jb20vbm90aWZpY2F0aW9uLnhtbDBhBgNVHR8EWjBYMFagVKBShlByc3luYzovL3Jwa2kucHJlcGRldi5yaXBlLm5ldC9yZXBvc2l0b3J5L0RFRkFVTFQvS3BTbzNWVks1d0VISUpuSEMyUUhWVjNkNW1rLmNybDAYBgNVHSABAf8EDjAMMAoGCCsGAQUFBw4CMCAGCCsGAQUFBwEHAQH/BBEwDzANBAIAAjAHAwUDKgupADAaBggrBgEFBQcBCAEB/wQLMAmgBzAFAgMDJ3gwDQYJKoZIhvcNAQELBQADggEBALAjs71otZ/CnOV24FIwWPoK+MKVzRJatk0NWdgBX8K2OLQYMgOw97PK8lgapNA2t63JofLkXXNP1wFFAlhg0rubf+iCHlgRkzasFioKn8TNsMqptKJRUDQ1NPNwzpmL75M89NQu1axQAgq2h8FgtYojTkdwsiMQeGNJfnQv2Ps/HtPL0hd9RsswJnpmRDdazN8+bvLNGdCk6Jk2YJ5dVWD79LwrREFQ+xOYX/r+zp/1BEBo7uyp3p5kaWb3Y5CM5NpCz8W+fdMTPiyhVuFpsQ+GkeryOkEMmVUVF4W4WtA3jhKx5CQpLOK7948WWozzw1hu9tudlbIefHfby5aUbNY=";

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

class NoopRepoFetcher implements RepoFetcher {
    private final String url;

    NoopRepoFetcher(String url) {
        this.url = url;
    }


    @Override
    public Map<String, RpkiObject> fetchObjects() throws FetcherException, SnapshotNotModifiedException {
        return Collections.emptyMap();
    }

    @Override
    public String repositoryUrl() {
        return url;
    }
}
