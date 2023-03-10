package net.ripe.rpki.monitor.expiration.fetchers;

import net.ripe.rpki.monitor.publishing.dto.RpkiObject;

import java.util.Map;

public interface RepoFetcher {

    Map<String, RpkiObject> fetchObjects() throws FetcherException, SnapshotStructureException, SnapshotNotModifiedException, RepoUpdateAbortedException;

    Meta meta();

    record Meta(String tag, String url) {}
}
