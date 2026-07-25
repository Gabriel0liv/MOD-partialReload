package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

public record TagDependencyGraph(Map<ResourceLocation, Set<ResourceLocation>> dependencies) {
    public TagDependencyGraph { dependencies = dependencies.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue()))); }
    public int edgeCount() { return dependencies.values().stream().mapToInt(Set::size).sum(); }
}
