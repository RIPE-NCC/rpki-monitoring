package net.ripe.rpki.monitor.expiration.fetchers;

public class SnapshotException extends RuntimeException {
    public SnapshotException(String msg) {
        super(msg);
    }
}
