package com.gabriel0liv.partialreload.core;

import java.time.Instant;

public record PartialReloadStatus(
        PartialReloadState state,
        int registeredProviders,
        int plannedIntegrations,
        Instant lastScanAt,
        int changedResources,
        String lastError
) {
}
