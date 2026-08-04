package com.gabriel0liv.partialreload.loot;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable complete generation installed in the active LootDataManager. */
public record ActiveLootDataGeneration(
        Map<LootDataId<?>, Object> elements,
        Multimap<LootDataType<?>, ResourceLocation> keysByType,
        UUID generationId,
        String compatibilityFingerprint
) {
    public ActiveLootDataGeneration {
        elements = Map.copyOf(elements);
        keysByType = ImmutableMultimap.copyOf(keysByType);
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(compatibilityFingerprint, "compatibilityFingerprint");
    }

    public int predicateCount() {
        return keysByType.get(LootDataType.PREDICATE).size();
    }

    public int itemModifierCount() {
        return keysByType.get(LootDataType.MODIFIER).size();
    }

    public int lootTableCount() {
        return keysByType.get(LootDataType.TABLE).size();
    }
}
