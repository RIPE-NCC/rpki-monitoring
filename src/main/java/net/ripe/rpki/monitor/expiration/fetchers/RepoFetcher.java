package net.ripe.rpki.monitor.expiration.fetchers;

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;

import java.util.Map;

public interface RepoFetcher {

    ImmutableMap<String, RpkiObject> fetchObjects() throws FetcherException, RRDPStructureException, SnapshotNotModifiedException, RepoUpdateAbortedException, RepoUpdateFailedException;

    Meta meta();

    record Meta(String tag, String url) {}
}
