package net.ripe.rpki.monitor.expiration.fetchers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;
import java.util.Map;

/**
 * Fetcher did not update repository for any non-fatal error reason.
 */
public class RepoUpdateAbortedException extends Exception{
    public static final String MESSAGE_TEMPLATE = "repository update aborted %s connect-to=%s: %s";

    public RepoUpdateAbortedException(@Nullable String url, Map<String, String> connectTo, String message) {
        super(MESSAGE_TEMPLATE.formatted(url, connectTo, message));
    }

    public RepoUpdateAbortedException(@Nullable String url, Map<String, String> connectTo, String message, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(url, connectTo, message), cause);
    }

    public RepoUpdateAbortedException(@Nullable String url, Map<String, String> connectTo, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(url, connectTo, cause.getMessage()), cause);
    }

    public RepoUpdateAbortedException(@Nullable URI uri, Map<String, String> connectTo, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(uri, connectTo, cause.getMessage()), cause);
    }
}
