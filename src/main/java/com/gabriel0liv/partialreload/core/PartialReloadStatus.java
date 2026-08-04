package com.gabriel0liv.partialreload.core;

import java.time.Instant;
import java.util.UUID;

public record PartialReloadStatus(
        PartialReloadState state,
        int registeredProviders,
        int plannedIntegrations,
        Instant lastScanAt,
        int changedResources,
        UUID preparedId,
        Boolean preparedApplicable,
        String lastError,
        long tagRecipeGeneration,
        boolean deferredClientRefreshEnabledForLastCommit,
        int staleClientCount,
        UUID lootGeneration,
        String lootTransactionStatus,
        boolean retainedLootRollbackGeneration,
        int activePredicateCount,
        int activeItemModifierCount,
        int activeLootTableCount
) {
}
