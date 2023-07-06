package net.ripe.rpki.monitor.expiration.fetchers;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;

public class RepoUpdateFailedException extends Exception {
    public static final String MESSAGE_TEMPLATE = "repository update failed: %s config=%s: %s";
    public RepoUpdateFailedException(String uri, RrdpHttpStrategy client, RrdpHttpStrategy.HttpResponseException cause) {
        super(MESSAGE_TEMPLATE.formatted(uri, client.describe(), cause.getMessage()), cause);
    }
}
