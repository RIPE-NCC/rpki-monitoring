package net.ripe.rpki.monitor.publishing.dto;

import java.io.Serializable;

public record RpkiObject (byte[] bytes) implements Serializable {
}
