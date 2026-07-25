package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Immutable logical view of a prepared tag generation; never exposes holders. */
public interface CandidateTagResolutionView {
    boolean registrySupported(String registryPath);
    boolean tagExists(String registryPath, ResourceLocation tagId);
    List<ResourceLocation> resolvedMembers(String registryPath, ResourceLocation tagId);
    boolean contains(String registryPath, ResourceLocation tagId, ResourceLocation elementId);
    default Set<ResourceLocation> allTagIds(String registryPath) { return Set.of(); }
    default Set<ResourceLocation> referencedTags(String registryPath, ResourceLocation tagId) { return Set.of(); }
    default TagResolutionResult resolve(String registryPath, ResourceLocation tagId) {
        if (!registrySupported(registryPath)) return new TagResolutionResult(TagResolutionStatus.REGISTRY_UNSUPPORTED, List.of(), registryPath);
        if (!tagExists(registryPath, tagId)) return new TagResolutionResult(TagResolutionStatus.TAG_MISSING, List.of(), tagId.toString());
        List<ResourceLocation> members = resolvedMembers(registryPath, tagId);
        return new TagResolutionResult(members.isEmpty() ? TagResolutionStatus.TAG_EMPTY : TagResolutionStatus.TAG_RESOLVED, members, tagId.toString());
    }
}
