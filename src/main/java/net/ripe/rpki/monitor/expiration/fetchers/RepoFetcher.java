package net.ripe.rpki.monitor.expiration.fetchers;

import java.util.Map;

public interface RepoFetcher {

    Map<String, byte[]> fetchObjects() throws FetcherException;
}
