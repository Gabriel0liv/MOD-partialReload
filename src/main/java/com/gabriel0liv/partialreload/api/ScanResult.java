package com.gabriel0liv.partialreload.api;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;

import java.util.Objects;

public record ScanResult(ResourceSnapshot snapshot) {
    public ScanResult {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
