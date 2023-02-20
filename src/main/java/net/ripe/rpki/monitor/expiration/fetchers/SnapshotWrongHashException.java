package net.ripe.rpki.monitor.expiration.fetchers;

public class SnapshotWrongHashException extends Exception {
    public SnapshotWrongHashException(String desiredHash, String realHash) {
        super("Snapshot hash (%s) is not the same as in notification.xml (%s)".formatted(realHash, desiredHash));
    }
}
