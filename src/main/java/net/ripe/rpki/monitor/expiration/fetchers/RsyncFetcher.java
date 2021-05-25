package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.monitor.RsyncConfig;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Setter
public class RsyncFetcher implements RepoFetcher {
    /**
     * Exit codes from <pre>man rsync</pre> that indicate that the rsync run was successful.
     */
    private static final Set<Integer> VALID_RSYNC_EXIT_CODES = Set.of(
            0, // Success
            24 // Partial transfer due to vanished source files - repo was updated during run.
    );

    private static final int DEFAULT_TIMEOUT = 30;

    private final int rsyncTimeout;

    private final Path targetPath;

    @Getter
    private final String name;
    private final String rsyncUrl;

    /** The URI that objects "appear" to be from. */
    private final String mainUrl;

    public RsyncFetcher(RsyncConfig rsyncConfig, String rsyncUrl) {
        this(rsyncConfig, rsyncUrl, rsyncUrl);
    }

    @SneakyThrows
    public RsyncFetcher(RsyncConfig rsyncConfig, String name, String rsyncUrl) {
        this.name = name;
        this.rsyncUrl = rsyncUrl;

        this.rsyncTimeout = rsyncConfig.getTimeout();
        this.mainUrl = rsyncConfig.getMainUrl();

        URI uri = URI.create(rsyncUrl);

        var basePath = rsyncConfig.getBaseDirectory();
        // Transform the rsync hostname:
        //  * Some (i.e. "file") rsync URLs do not have a host - make sure they are formatted as null.
        //  * Replace dots with underscores
        var transformedHost = String.format("%s", uri.getHost()).replace(".", "_");
        targetPath = basePath.resolve(transformedHost);
        if (!targetPath.normalize().startsWith(basePath)) {
            throw new IllegalArgumentException(String.format("Directory traversal detected - %s is not below %s", targetPath, basePath));
        }

        Files.createDirectories(targetPath);
        log.info("RsyncFetcher({}, {}) -> {}", name, rsyncUrl, targetPath.toString());
    }

    @Override
    public String repositoryUrl() {
        return rsyncUrl;
    }

    private void rsyncPathFromRepository(String url, Path localPath) throws FetcherException {
        final var rsync = new Rsync(url, localPath.toString());
        // rsync flags from routinator except contimeout (not available on osx and in CI/CD)
        rsync.addOptions("-rltz", "--delete");
        rsync.setTimeoutInSeconds(rsyncTimeout);

        log.info("Running rsync {} to {}", url, localPath.toString());
        final var t0 = System.currentTimeMillis();
        final var exitCode = rsync.execute();
        if (!VALID_RSYNC_EXIT_CODES.contains(exitCode)) {
            throw new FetcherException(String.format("rsync from %s to %s exited with %d", url, localPath, exitCode));
        }
        log.info("rsync  {} to {} finished in {} seconds.", url, localPath.toString(), rsync.elapsedTime() / 1000.0);
    }


    public Map<String, RpkiObject> fetchObjects() throws FetcherException {
        try {
            rsyncPathFromRepository(rsyncUrl + "/ta", targetPath.resolve("ta"));
            rsyncPathFromRepository(rsyncUrl + "/repository", targetPath.resolve("repository"));

            // Gather all objects in path
            try (Stream<Path> paths = Files.walk(targetPath)) {
                return paths.filter(Files::isRegularFile)
                    .parallel()
                    .map(f -> {
                        // Object "appear" to be in the main repository, otherwise they will always
                        // mismatch because of their URL.
                        final String objectUri = f.toString().replace(targetPath.toString(), mainUrl);
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
        }
    }
}
