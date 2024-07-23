package net.ripe.rpki.monitor.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpSnapshotClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@UtilityClass
public class RrdpContent {
    public static RrdpSnapshotClient.RrdpSnapshotState prefetch(TrustAnchor ta) {
        var http = new CachedRrdpHttp();

        var client = new RrdpSnapshotClient(http);
        try {
            return client.fetchObjects(ta.notificationUri(), Optional.empty());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public enum TrustAnchor {
        APNIC("https://rrdp.apnic.net/notification.xml"),
        RIPE("https://rrdp.ripe.net/notification.xml"),
        RIPE_PILOT("https://localcert.ripe.net/rrdp/notification.xml");

        private final String notificationUri;

        TrustAnchor(String notificationUri) {
            this.notificationUri = notificationUri;
        }

        public String notificationUri() {
            return notificationUri;
        }
    }

    /**
     * RRDP HTTP implementation that caches results in the build directory. Cache expiration is at the day boundary.
     */
    private static class CachedRrdpHttp implements RrdpHttp {
        private final String timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        @SneakyThrows
        @Override
        public byte[] fetch(String uri) {
            var url = URI.create(uri);
            var cacheFile = Path.of(String.format(
                    "build/integration/data/%s-%s-%s",
                    timestamp,
                    url.getHost().replaceAll("\\.", "_"),
                    filename(url)
            ));
            if (Files.exists(cacheFile)) {
                return Files.readAllBytes(cacheFile);
            }

            try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
                var req = HttpRequest.newBuilder(URI.create(uri))
                        .GET()
                        .header("accept-encoding", "gzip")
                        .build();
                var response = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() > 299) {
                    throw new RuntimeException(String.format("fetching %s returned with HTTP status %d", uri, response.statusCode()));
                }
                byte[] data;
                if (response.headers().firstValue("content-encoding").equals(Optional.of("gzip"))) {
                    try (var gzip = new GZIPInputStream(response.body()); var os = new ByteArrayOutputStream()) {
                        gzip.transferTo(os);
                        data = os.toByteArray();
                    }
                } else {
                    try (var is = response.body()) {
                        data = is.readAllBytes();
                    }
                }
                Files.createDirectories(cacheFile.getParent());
                Files.write(cacheFile, data);
                return data;
            }
        }

        private String filename(URI uri) {
            var path = uri.getPath().split("/");
            return path[path.length-1];
        }

        @Override
        public String transformHostname(String url) {
            return url;
        }
    }
}
