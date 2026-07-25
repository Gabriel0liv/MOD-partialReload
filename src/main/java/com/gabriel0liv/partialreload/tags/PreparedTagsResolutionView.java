package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds candidate resolution exclusively from an immutable PreparedTags artifact. */
public final class PreparedTagsResolutionView implements CandidateTagResolutionView {
    private final PreparedTags tags;

    public PreparedTagsResolutionView(PreparedTags tags) {
        this.tags = java.util.Objects.requireNonNull(tags, "tags");
    }

    @Override public boolean registrySupported(String registryPath) {
        return tags.registries().containsKey(registryPath);
    }

    @Override public boolean tagExists(String registryPath, ResourceLocation tagId) {
        PreparedRegistryTags registry = tags.registries().get(registryPath);
        return registry != null && registry.tags().containsKey(tagId);
    }

    @Override public List<ResourceLocation> resolvedMembers(String registryPath, ResourceLocation tagId) {
        PreparedRegistryTags registry = tags.registries().get(registryPath);
        if (registry == null || !registry.tags().containsKey(tagId)) return List.of();
        return resolve(registry, tagId, new LinkedHashSet<>());
    }

    private List<ResourceLocation> resolve(PreparedRegistryTags registry, ResourceLocation id, Set<ResourceLocation> visiting) {
        if (!visiting.add(id)) return List.of();
        PreparedTag tag = registry.tags().get(id);
        if (tag == null) return List.of();
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (String entry : tag.orderedEntries()) {
            if (entry.startsWith("#")) {
                try { result.addAll(resolve(registry, ResourceLocation.parse(entry.substring(1)), visiting)); }
                catch (IllegalArgumentException ignored) { }
            } else if (!tag.missingOptionalEntries().contains(entry)) {
                try { result.add(ResourceLocation.parse(entry)); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        visiting.remove(id);
        return List.copyOf(result);
    }

    @Override public boolean contains(String registryPath, ResourceLocation tagId, ResourceLocation elementId) {
        return resolvedMembers(registryPath, tagId).contains(elementId);
    }
}
