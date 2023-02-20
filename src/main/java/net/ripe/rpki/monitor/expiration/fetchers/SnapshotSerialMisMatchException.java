package net.ripe.rpki.monitor.expiration.fetchers;

public class SnapshotSerialMisMatchException extends SnapshotException {
    public SnapshotSerialMisMatchException(final String url, final int expected, final int observed) {
        super("Serial of notification.xml refers to snapshot at %s with expected serial of %d, observed=%d".formatted(url,expected, observed));
    }
}
