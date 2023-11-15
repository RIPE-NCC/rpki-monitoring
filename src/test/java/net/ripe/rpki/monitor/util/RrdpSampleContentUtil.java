package net.ripe.rpki.monitor.util;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import net.ripe.rpki.monitor.certificateanalysis.CertificateAnalysisServiceTest;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpSnapshotClient;
import net.ripe.rpki.monitor.fetchers.RrdpSnapshotClientTest;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.springframework.core.io.ClassPathResource;

import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RrdpSampleContentUtil {
    @SneakyThrows
    public static ImmutableMap<String, RpkiObject> rpkiObjects(String notificationClasspathPath, String snapshotClasspathPath) {
        // dirty setup to get RRDP objects from the mock data
        var mockHttp = mock(RrdpHttp.class);

        when(mockHttp.fetch(any())).thenReturn(readMaybeGzippedFile(notificationClasspathPath), readMaybeGzippedFile(snapshotClasspathPath));
        when(mockHttp.transformHostname(any())).thenAnswer(i -> i.getArguments()[0]);

        return new RrdpSnapshotClient(mockHttp).fetchObjects(RrdpSnapshotClientTest.EXAMPLE_ORG_NOTIFICATION_XML, Optional.empty()).objects();
    }

    @SneakyThrows
    static byte[] readMaybeGzippedFile(String path) {
        var resource = new ClassPathResource(path);

        var tokens = resource.getFilename().split("\\.");

        switch (tokens[tokens.length-1]) {
            case "gz":
                return new GZIPInputStream(resource.getInputStream()).readAllBytes();
            default:
                return resource.getInputStream().readAllBytes();
        }
    }
}
