package net.ripe.rpki.monitor.expiration.fetchers;

public class RepoUpdateFailedException extends Exception {
    public static final String MESSAGE_TEMPLATE = "repository update failed: %s config=%s: %s";
    public RepoUpdateFailedException(String uri, RrdpHttpStrategy client, RrdpHttpStrategy.HttpResponseException cause) {
        super(MESSAGE_TEMPLATE.formatted(uri, client.describe(), cause.getMessage()), cause);
    }
}