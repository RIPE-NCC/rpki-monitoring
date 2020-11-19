package net.ripe.rpki.monitor.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;

public class Sha256 {
    public static String asString(byte[] bytes) {
        return calculateHash(bytes).toString().toLowerCase();
    }

    public static String asString(String content) {
        return asString(content.getBytes());
    }

    public static byte[] asBytes(byte[] bytes) {
        return calculateHash(bytes).asBytes();
    }

    public static byte[] asBytes(RpkiObject rpkiObject) {
        return asBytes(rpkiObject.getBytes());
    }

    private static HashCode calculateHash(byte[] bytes) {
        return Hashing.sha256().hashBytes(bytes);
    }
}
