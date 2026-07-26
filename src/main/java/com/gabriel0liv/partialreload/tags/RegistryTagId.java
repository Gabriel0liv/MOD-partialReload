package com.gabriel0liv.partialreload.tags;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registry-qualified tag identity; a tag id alone is not globally unique. */
public record RegistryTagId(ResourceKey<? extends Registry<?>> registry, ResourceLocation tagId) {
    public RegistryTagId {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(tagId, "tagId");
    }
}
