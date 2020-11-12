package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Setter
public class RsyncFetcher implements RepoFetcher {

    private static final int DEFAULT_TIMEOUT = 30;

    private int rsyncTimeout;
    private String rsyncUrl;

    public RsyncFetcher(String rsyncUrl) {
        this(rsyncUrl, DEFAULT_TIMEOUT);
    }

    public RsyncFetcher(String rsyncUrl, int rsyncTimeout) {
        this.rsyncUrl = rsyncUrl;
        this.rsyncTimeout = rsyncTimeout;
    }

    @Override
    public String repositoryUrl() {
        return rsyncUrl;
    }

    private void rsyncPathFromRepository(String url, Path localPath) throws FetcherException {
        final var rsync = new Rsync(url, localPath.toString());
        rsync.addOptions("-a");
        rsync.setTimeoutInSeconds(rsyncTimeout);

        final var exitCode = rsync.execute();
        if (exitCode != 0) {
            throw new FetcherException(String.format("rsync from %s exited with %d", url, exitCode));
        }
    }


    public Map<String, RpkiObject> fetchObjects() throws FetcherException {
        Path tempPath = null;
        try {
            tempPath = Files.createTempDirectory("rsync-monitor");
            final String tempDirectory = tempPath.toString();

            log.info("running rsync for '{}' to {}", this.rsyncUrl, tempPath.toString());
            final var t0 = System.currentTimeMillis();

            rsyncPathFromRepository(rsyncUrl + "/ta", tempPath.resolve("ta"));
            rsyncPathFromRepository(rsyncUrl + "/repository", tempPath.resolve("repository"));

            log.info("rsync finished in {} seconds.", Math.round((System.currentTimeMillis()-t0)/100.0)/10.0);

            final Map<String, RpkiObject> objects = new HashMap<>();
            Files.walk(tempPath)
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        final String objectUri = f.toString().replace(tempDirectory, rsyncUrl);
                        try {
                            objects.put(objectUri, new RpkiObject(Files.readAllBytes(f)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            return objects;
        } catch (IOException | RuntimeException e) {
            throw new FetcherException(e);
        } finally {
            try {
                FileSystemUtils.deleteRecursively(tempPath);
            } catch (IOException e) {
                log.error("Exception while cleaning up after rsync", e);
            }
        }
    }

}
