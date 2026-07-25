package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record TagDelta(Set<ResourceLocation> tagsAdded, Set<ResourceLocation> tagsModified,
                       Set<ResourceLocation> tagsRemoved, Set<String> membersAdded,
                       Set<String> membersRemoved, Set<ResourceLocation> tagsRestoredFromLowerPack,
                       boolean replaceChanged, boolean contributingPacksChanged) {
    public TagDelta { tagsAdded=Set.copyOf(tagsAdded); tagsModified=Set.copyOf(tagsModified); tagsRemoved=Set.copyOf(tagsRemoved); membersAdded=Set.copyOf(membersAdded); membersRemoved=Set.copyOf(membersRemoved); tagsRestoredFromLowerPack=Set.copyOf(tagsRestoredFromLowerPack); }
}
