package net.ripe.rpki.monitor.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class RpkiObject {
    byte[] bytes;
}
