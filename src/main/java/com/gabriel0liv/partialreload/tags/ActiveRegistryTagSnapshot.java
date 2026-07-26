package com.gabriel0liv.partialreload.tags;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public record ActiveRegistryTagSnapshot(ResourceKey<? extends Registry<?>> registry,
                                        int registryIdentity,
                                        Map<ResourceLocation, ActiveTagSnapshot> tags) {
    public ActiveRegistryTagSnapshot {
        tags = Map.copyOf(tags);
    }
}
