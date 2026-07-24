package com.gabriel0liv.partialreload.loot;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record LootDependency(
        LootDataKind sourceKind,
        ResourceLocation source,
        LootDataKind targetKind,
        ResourceLocation target,
        LootDependencyType type,
        String jsonPath
) {
    public LootDependency {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jsonPath, "jsonPath");
    }
}

