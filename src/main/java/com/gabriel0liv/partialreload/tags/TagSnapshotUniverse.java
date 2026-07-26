package com.gabriel0liv.partialreload.tags;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record TagSnapshotUniverse(ResourceKey<? extends Registry<?>> registry, Set<ResourceLocation> tagIds) {
    public TagSnapshotUniverse { tagIds = tagIds.stream().sorted().collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
