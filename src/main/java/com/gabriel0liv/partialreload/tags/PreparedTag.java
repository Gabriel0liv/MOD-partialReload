package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;

public record PreparedTag(String registryPath, ResourceLocation id, String logicalPath,
                          List<String> contributingPacks, List<String> contentHashes,
                          boolean replace, List<String> orderedEntries,
                          List<String> removedEntries, Set<ResourceLocation> referencedTags,
                          Set<String> missingOptionalEntries) {
    public PreparedTag {
        contributingPacks = List.copyOf(contributingPacks); contentHashes = List.copyOf(contentHashes);
        orderedEntries = List.copyOf(orderedEntries); removedEntries = List.copyOf(removedEntries);
        referencedTags = Set.copyOf(referencedTags); missingOptionalEntries = Set.copyOf(missingOptionalEntries);
    }
}
