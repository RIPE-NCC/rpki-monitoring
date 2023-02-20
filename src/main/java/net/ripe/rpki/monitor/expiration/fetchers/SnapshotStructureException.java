package net.ripe.rpki.monitor.expiration.fetchers;

public class SnapshotStructureException extends SnapshotException {
    public SnapshotStructureException(String msg) {
        super("Structure of snapshot file did not match expected structure: %s".formatted(msg));
    }
}
