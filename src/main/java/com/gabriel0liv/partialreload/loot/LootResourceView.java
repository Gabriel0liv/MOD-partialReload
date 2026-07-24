package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.util.Map;

record LootResourceView(
        ResourceSnapshot snapshot,
        Map<ResourceLocation, String> stackFingerprints,
        Map<ResourceLocation, java.util.List<String>> packStacks,
        Map<LootDataKind, Map<ResourceLocation, Source>> sources,
        boolean hasGlobalLootModifiers
) {
    LootResourceView {
        stackFingerprints = Map.copyOf(stackFingerprints);
        packStacks = packStacks.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> java.util.List.copyOf(entry.getValue())
                )
        );
        sources = Map.copyOf(sources);
    }

    record Source(
            LootDataKind kind,
            ResourceLocation id,
            ResourceLocation file,
            Resource winner,
            ResourceDescriptor descriptor,
            JsonElement json
    ) {
    }
}
