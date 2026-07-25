package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable logical view of a prepared tag generation; never exposes holders. */
public interface CandidateTagResolutionView {
    boolean registrySupported(String registryPath);
    boolean tagExists(String registryPath, ResourceLocation tagId);
    List<ResourceLocation> resolvedMembers(String registryPath, ResourceLocation tagId);
    boolean contains(String registryPath, ResourceLocation tagId, ResourceLocation elementId);
}
