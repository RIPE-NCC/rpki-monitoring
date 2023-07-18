package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.monitor.publishing.dto.RpkiObject;

import java.util.function.Consumer;

public interface ObjectConsumer extends Consumer<ImmutableMap<String, RpkiObject>> {
}
