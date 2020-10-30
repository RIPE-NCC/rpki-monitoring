package net.ripe.rpki.monitor;

public interface HasHashAndUri {
    String getSha256();
    String getUri();
}
