package net.ripe.rpki.monitor.expiration.fetchers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;

/**
 * Fetcher did not update repository for any non-fatal error reason.
 */
public class RepoUpdateAbortedException extends Exception{
    public static final String MESSAGE_TEMPLATE = "repository update aborted %s connect-to=%s: %s";

    public RepoUpdateAbortedException(@Nullable String url, RrdpHttp http, String message) {
        super(MESSAGE_TEMPLATE.formatted(url, http.describe(), message));
    }

    public RepoUpdateAbortedException(@Nullable String url, RrdpHttp http, String message, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(url, http.describe(), message), cause);
    }

    public RepoUpdateAbortedException(@Nullable String url, RrdpHttp http, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(url, http.describe(), cause.getMessage()), cause);
    }

    public RepoUpdateAbortedException(@Nullable URI uri, RrdpHttp http, Throwable cause) {
        super(MESSAGE_TEMPLATE.formatted(uri, http.describe(), cause.getMessage()), cause);
    }
}
