package net.ripe.rpki.monitor.util;

import com.google.common.hash.Hashing;

public class Sha256 {
    public static String asString(byte[] bytes) {
        return Hashing.sha256().hashBytes(bytes).toString().toLowerCase();
    }

    public static String asString(String content) {
        return asString(content.getBytes());
    }
}
