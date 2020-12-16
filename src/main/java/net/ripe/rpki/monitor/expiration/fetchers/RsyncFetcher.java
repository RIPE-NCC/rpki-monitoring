package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        log.info("Running rsync {} to {}", url, localPath.toString());
        final var t0 = System.currentTimeMillis();
        final var exitCode = rsync.execute();
        if (exitCode != 0) {
            throw new FetcherException(String.format("rsync from %s to %s exited with %d", url, localPath, exitCode));
        }
        log.info("rsync  {} to {} finished in {} seconds.", url, localPath.toString(), (System.currentTimeMillis() - t0) / 1000);
    }


    public Map<String, RpkiObject> fetchObjects() throws FetcherException {
        Path tempPath = null;
        try {
            tempPath = Files.createTempDirectory("rsync-monitor");
            final String tempDirectory = tempPath.toString();

            rsyncPathFromRepository(rsyncUrl + "/ta", tempPath.resolve("ta"));
            rsyncPathFromRepository(rsyncUrl + "/repository", tempPath.resolve("repository"));

            // Gather all objects in path
            try (Stream<Path> paths = Files.walk(tempPath)) {
                return paths.filter(Files::isRegularFile)
                    .parallel()
                    .map(f -> {
                        final String objectUri = f.toString().replace(tempDirectory, rsyncUrl);
                        try {
                            return Pair.of(objectUri, new RpkiObject(Files.readAllBytes(f)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toConcurrentMap(Pair::getKey, Pair::getValue));
            }
        } catch (IOException | RuntimeException e) {
            log.error("Rsync fetch failed", e);
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
