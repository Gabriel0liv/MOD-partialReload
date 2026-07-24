package com.gabriel0liv.partialreload.loot;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record LootTypeDelta(Map<ResourceLocation, LootDataChangeKind> changes) {
    public LootTypeDelta {
        changes = Map.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public Set<ResourceLocation> ofKind(LootDataChangeKind kind) {
        return changes.entrySet().stream()
                .filter(entry -> entry.getValue() == kind)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public long changedCount() {
        return changes.values().stream().filter(kind -> kind != LootDataChangeKind.UNCHANGED).count();
    }
}

