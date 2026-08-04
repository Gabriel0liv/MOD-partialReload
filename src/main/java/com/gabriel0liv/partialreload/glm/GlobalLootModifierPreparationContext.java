package com.gabriel0liv.partialreload.glm;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record GlobalLootModifierPreparationContext(
        ResourceManager resourceManager,
        ResourceSnapshot sourceSnapshot,
        @Nullable ResourceSnapshot activeReference,
        List<net.minecraft.resources.ResourceLocation> activeOrderedIds,
        Clock clock,
        Supplier<UUID> idSupplier
) {
    public GlobalLootModifierPreparationContext {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        activeOrderedIds = List.copyOf(Objects.requireNonNull(activeOrderedIds));
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(idSupplier, "idSupplier");
    }
}
