package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.base.Verify;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.monitor.config.RsyncConfig;
import net.ripe.rpki.monitor.metrics.FetcherMetrics;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.appendIfMissing;
import static org.apache.commons.lang3.StringUtils.removeEnd;

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

    @Getter(AccessLevel.PACKAGE)
    private final Path targetPath;

    @Getter
    private final String name;
    private final String rsyncUrl;

    /** The URI that objects "appear" to be from. */
    private final String repositoryUrl;

    private final FetcherMetrics.RsyncFetcherMetrics metrics;
    private final List<String> directories;

    @SneakyThrows
    public RsyncFetcher(RsyncConfig rsyncConfig, String name, String rsyncUrl, FetcherMetrics fetcherMetrics) {
        this.name = name;
        this.rsyncUrl = removeEnd(rsyncUrl, "/");

        this.rsyncTimeout = rsyncConfig.getTimeout();
        this.repositoryUrl = removeEnd(rsyncConfig.getRepositoryUrl(), "/");
        this.directories = rsyncConfig.getDirectories();
        this.metrics = fetcherMetrics.rsync(this.rsyncUrl);

        URI uri = URI.create(rsyncUrl);

        var basePath = rsyncConfig.getBaseDirectory();
        // Transform the rsync hostname:
        //  * Some (i.e. "file") rsync URLs do not have a host - make sure they are formatted as null.
        //  * Replace dots with underscores
        var transformedHost = String.format("%s", uri.getHost()).replace(".", "_");
        targetPath = basePath.resolve(transformedHost);
        // Host should not be able to contain dots, but let's double check
        Verify.verify(targetPath.normalize().startsWith(basePath), String.format("Directory traversal detected - %s is not below %s", targetPath, basePath));

        Files.createDirectories(targetPath);
        log.info("RsyncFetcher({}, {}) -> {}", name, rsyncUrl, targetPath);
    }

    @Override
    public Meta meta() {
        return new Meta(name, rsyncUrl);
    }

    private void rsyncPathFromRepository(String url, Path localPath) throws FetcherException {
        // Detect path traversal here - should be trusted value
        Verify.verify(localPath.normalize().startsWith(targetPath));

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

    @Override
    public Map<String, RpkiObject> fetchObjects() throws FetcherException {
        try {
            for (var directory : directories) {
                rsyncPathFromRepository(rsyncUrl + "/" + appendIfMissing(directory, "/"), targetPath.resolve(directory));
            }

            // Gather all objects in path
            try (Stream<Path> paths = Files.walk(targetPath)) {
                var res = paths.filter(Files::isRegularFile)
                    .parallel()
                    .map(f -> {
                        // Object "appear" to be in the main repository, otherwise they will always
                        // mismatch because of their URL.
                        final String objectUri = f.toString().replace(targetPath.toString(), repositoryUrl);
                        try {
                            return Pair.of(objectUri, new RpkiObject(Files.readAllBytes(f)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toConcurrentMap(Pair::getKey, Pair::getValue));
                metrics.success();
                return res;
            }
        } catch (IOException | RuntimeException e) {
            log.error("Rsync fetch failed", e);
            metrics.failure();
            throw new FetcherException(e);
        }
    }
}
