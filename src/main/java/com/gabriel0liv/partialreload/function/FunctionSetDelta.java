package com.gabriel0liv.partialreload.function;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record FunctionSetDelta(
        Set<ResourceLocation> added,
        Set<ResourceLocation> removed,
        Set<ResourceLocation> retained
) {
    public FunctionSetDelta {
        added = Set.copyOf(added);
        removed = Set.copyOf(removed);
        retained = Set.copyOf(retained);
    }

    public static FunctionSetDelta between(Set<ResourceLocation> active, Set<ResourceLocation> prepared) {
        java.util.HashSet<ResourceLocation> added = new java.util.HashSet<>(prepared);
        added.removeAll(active);
        java.util.HashSet<ResourceLocation> removed = new java.util.HashSet<>(active);
        removed.removeAll(prepared);
        java.util.HashSet<ResourceLocation> retained = new java.util.HashSet<>(active);
        retained.retainAll(prepared);
        return new FunctionSetDelta(added, removed, retained);
    }

    public boolean changed() {
        return !added.isEmpty() || !removed.isEmpty();
    }
}
