package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Setter
@Component("RsyncFetcher")
public class RsyncFetcher implements RepoFetcher {
    @Value("${rsync.timeout}")
    private int rsyncTimeout;

    @Value("${rsync.url}")
    private String rsyncUrl;

    private int rsyncPathFromRepository(String url, Path localPath) throws FetcherException {
        final var rsync = new Rsync(url, localPath.toString());
        rsync.addOptions("-a");
        rsync.setTimeoutInSeconds(rsyncTimeout);

        final var exitCode = rsync.execute();
        if (exitCode != 0) {
            throw new FetcherException(String.format("rsync from %s exited with %d", url, exitCode));
        }

        return exitCode;
    }


    public Map<String, byte[]> fetchObjects() throws FetcherException {
        Path tempPath = null;
        try {
            tempPath = Files.createTempDirectory("rsync-monitor");
            final String tempDirectory = tempPath.toString();

            log.info("running rsync for '{}' to {}", this.rsyncUrl, tempPath.toString());
            final var t0 = System.currentTimeMillis();

            rsyncPathFromRepository(rsyncUrl + "/ta", tempPath.resolve("ta"));
            rsyncPathFromRepository(rsyncUrl + "/repository", tempPath.resolve("repository"));

            log.info("rsync finished in {} seconds.", Math.round((System.currentTimeMillis()-t0)/100.0)/10.0);

            final Map<String, byte[]> objects = new HashMap<>();
            Files.walk(tempPath)
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        final String objectUri = f.toString().replace(tempDirectory, rsyncUrl);
                        try {
                            objects.put(objectUri, Files.readAllBytes(f));
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
