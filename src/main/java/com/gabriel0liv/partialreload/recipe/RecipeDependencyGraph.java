package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

public record RecipeDependencyGraph(Map<ResourceLocation, Set<ResourceLocation>> dependencies) {
    public RecipeDependencyGraph {
        dependencies = dependencies.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }
}
