package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

final class LootDeltaCalculator {
    LootDataDelta between(
            @Nullable ResourceSnapshot before,
            ResourceSnapshot after,
            Map<ResourceLocation, java.util.List<String>> currentPackStacks
    ) {
        return new LootDataDelta(
                forKind(LootDataKind.PREDICATE, before, after, currentPackStacks),
                forKind(LootDataKind.ITEM_MODIFIER, before, after, currentPackStacks),
                forKind(LootDataKind.LOOT_TABLE, before, after, currentPackStacks)
        );
    }

    private LootTypeDelta forKind(
            LootDataKind kind,
            @Nullable ResourceSnapshot before,
            ResourceSnapshot after,
            Map<ResourceLocation, java.util.List<String>> currentPackStacks
    ) {
        Map<ResourceLocation, ResourceDescriptor> oldById = byId(kind, before);
        Map<ResourceLocation, ResourceDescriptor> newById = byId(kind, after);
        Map<ResourceLocation, LootDataChangeKind> changes = new LinkedHashMap<>();
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>(oldById.keySet());
        ids.addAll(newById.keySet());
        for (ResourceLocation id : ids) {
            ResourceDescriptor oldValue = oldById.get(id);
            ResourceDescriptor newValue = newById.get(id);
            LootDataChangeKind change;
            if (oldValue == null) change = LootDataChangeKind.ADDED;
            else if (newValue == null) change = LootDataChangeKind.REMOVED;
            else if (oldValue.fingerprint().equals(newValue.fingerprint())
                    && oldValue.sourcePack().equals(newValue.sourcePack())) {
                change = LootDataChangeKind.UNCHANGED;
            } else if (!oldValue.sourcePack().equals(newValue.sourcePack())
                    && !currentPackStacks.getOrDefault(
                            newValue.location(), java.util.List.of()
                    ).contains(oldValue.sourcePack())) {
                change = LootDataChangeKind.RESTORED_FROM_LOWER_PACK;
            } else {
                change = LootDataChangeKind.MODIFIED;
            }
            changes.put(id, change);
        }
        return new LootTypeDelta(changes);
    }

    private Map<ResourceLocation, ResourceDescriptor> byId(
            LootDataKind kind,
            @Nullable ResourceSnapshot snapshot
    ) {
        if (snapshot == null) return Map.of();
        Map<ResourceLocation, ResourceDescriptor> result = new LinkedHashMap<>();
        snapshot.resources().values().stream()
                .filter(descriptor -> descriptor.category() == kind.category())
                .forEach(descriptor -> result.put(descriptor.logicalId(), descriptor));
        return result;
    }
}
