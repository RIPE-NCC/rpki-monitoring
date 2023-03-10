package net.ripe.rpki.monitor.expiration.fetchers;

import java.net.URI;
import java.util.Map;

/**
 * Fetcher did not update repository for any non-fatal error reason.
 */
public class RepoUpdateAbortedException extends Exception{
    public RepoUpdateAbortedException(String url, Map<String, String> connectTo, String message) {
        super("repository update aborted %s connect-to=%s: %s".formatted(url, connectTo, message));
    }

    public RepoUpdateAbortedException(String url, Map<String, String> connectTo, String message, Throwable cause) {
        super("repository update aborted %s connect-to=%s: %s".formatted(url, connectTo, message), cause);
    }

    public RepoUpdateAbortedException(String url, Map<String, String> connectTo, Throwable cause) {
        super("repository update aborted %s connect-to=%s: %s".formatted(url, connectTo, cause.getMessage()), cause);
    }

    public RepoUpdateAbortedException(URI uri, Map<String, String> connectTo, Throwable cause) {
        super("repository update aborted %s connect-to=%s: %s".formatted(uri, connectTo, cause.getMessage()), cause);
    }
}
