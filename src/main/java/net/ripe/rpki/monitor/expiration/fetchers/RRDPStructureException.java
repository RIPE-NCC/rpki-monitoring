package net.ripe.rpki.monitor.expiration.fetchers;

public class RRDPStructureException extends Exception {
    public RRDPStructureException(String url, String msg) {
        super("RRDP content at %s did not match expected structure: %s".formatted(url, msg));
    }
}
