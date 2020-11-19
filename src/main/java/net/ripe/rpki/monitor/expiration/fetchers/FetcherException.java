package net.ripe.rpki.monitor.expiration.fetchers;

public class FetcherException extends RuntimeException {

    public FetcherException(final Throwable e) {
        super(e);
    }

    public FetcherException(final String cause) {
        super(cause);
    }
}
