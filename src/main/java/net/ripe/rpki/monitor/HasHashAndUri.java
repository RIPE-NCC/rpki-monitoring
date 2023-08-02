package net.ripe.rpki.monitor;

import com.google.common.hash.HashCode;

public interface HasHashAndUri {
    byte[] sha256();
    String getUri();

    default String getSha256() {
        return HashCode.fromBytes(sha256()).toString();
    }
}
