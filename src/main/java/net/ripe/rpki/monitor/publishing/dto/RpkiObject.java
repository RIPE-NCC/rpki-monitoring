package net.ripe.rpki.monitor.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class RpkiObject {
    private byte[] bytes;
}
