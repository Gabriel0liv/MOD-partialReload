package com.gabriel0liv.partialreload.api;

import net.minecraft.server.packs.resources.ResourceManager;

import java.time.Duration;
import java.util.Objects;

public record ScanContext(
        ResourceManager resourceManager,
        int maxResources,
        Duration timeout,
        boolean reportUnknownResources
) {
    public ScanContext {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Objects.requireNonNull(timeout, "timeout");
        if (maxResources < 1) {
            throw new IllegalArgumentException("maxResources must be positive");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
