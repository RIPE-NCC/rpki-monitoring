package net.ripe.rpki.monitor.compare;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import net.ripe.rpki.monitor.expiration.fetchers.RsyncFetcher;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;
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
        fetcher1 = new RsyncFetcher(url1);
        fetcher2 = new RsyncFetcher(url2);
    }

    Optional<Pair<Map<String, RpkiObject>, Map<String, RpkiObject>>> fetchBoth() {
        try {
            final CompletableFuture<Map<String, RpkiObject>> f1 = CompletableFuture.supplyAsync(() -> fetchRepo(fetcher1));
            final CompletableFuture<Map<String, RpkiObject>> f2 = CompletableFuture.supplyAsync(() -> fetchRepo(fetcher2));
            return Optional.of(Pair.of(f1.get(), f2.get()));
        } catch (Exception e) {
            log.error("Fetch error: ", e);
            return Optional.empty();
        }
    }

    private Map<String, RpkiObject> fetchRepo(RsyncFetcher fetcher) {
        try {
            return fetcher.fetchObjects();
        } catch (FetcherException e) {
            throw new RuntimeException(e);
        }
    }

}
