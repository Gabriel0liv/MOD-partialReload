package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public record LootPreparationContext(
        ResourceManager resourceManager,
        Set<ReloadCategory> requestedCategories,
        @Nullable ResourceSnapshot activeReference,
        Duration timeout,
        int maxPredicates,
        int maxItemModifiers,
        int maxLootTables,
        long maxTotalJsonBytes,
        int maxDependencyEdges,
        Clock clock,
        Supplier<UUID> idSupplier,
        LongSupplier nanoTime
) {
    public LootPreparationContext {
        Objects.requireNonNull(resourceManager, "resourceManager");
        requestedCategories = Set.copyOf(requestedCategories);
        if (requestedCategories.isEmpty()
                || !PreparedLootData.COMPLETE_SCOPE.containsAll(requestedCategories)) {
            throw new IllegalArgumentException("requestedCategories must contain loot categories only");
        }
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(idSupplier, "idSupplier");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (maxPredicates < 1 || maxItemModifiers < 1 || maxLootTables < 1
                || maxTotalJsonBytes < 1 || maxDependencyEdges < 1) {
            throw new IllegalArgumentException("loot preparation limits must be positive");
        }
    }
}

