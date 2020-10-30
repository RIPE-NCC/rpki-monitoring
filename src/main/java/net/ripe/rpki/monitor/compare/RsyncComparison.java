package net.ripe.rpki.monitor.compare;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches two rsync repositories, compares them and reports the result.
 */
@Slf4j
public class RsyncComparison {

    private RsyncFetcher fetcher1;
    private RsyncFetcher fetcher2;

    public RsyncComparison(String url1, String url2) {
        fetcher1 = new RsyncFetcher();
        fetcher1.setRsyncUrl(url1);
        fetcher2 = new RsyncFetcher();
        fetcher2.setRsyncUrl(url2);
    }

    void compare() {
        Optional<Pair<Map<String, byte[]>, Map<String, byte[]>>> maps = fetchBoth();
        if (maps.isEmpty()) {
            log.info("Could not fetch at least one of the repositories, bailing out");
        } else {
            Map<String, byte[]> m1 = maps.get().getLeft();
            Map<String, byte[]> m2 = maps.get().getRight();

        }
    }
    
    Optional<Pair<Map<String, byte[]>, Map<String, byte[]>>> fetchBoth() {
        try {
            final CompletableFuture<Map<String, byte[]>> f1 = CompletableFuture.supplyAsync(() -> fetchRepo(fetcher1));
            final CompletableFuture<Map<String, byte[]>> f2 = CompletableFuture.supplyAsync(() -> fetchRepo(fetcher2));
            return Optional.of(Pair.of(f1.get(), f2.get()));
        } catch (Exception e) {
            log.error("Fetch error: ", e);
            return Optional.empty();
        }
    }

    private Map<String, byte[]> fetchRepo(RsyncFetcher fetcher) {
        try {
            return fetcher.fetchObjects();
        } catch (FetcherException e) {
            throw new RuntimeException(e);
        }
    }

}
