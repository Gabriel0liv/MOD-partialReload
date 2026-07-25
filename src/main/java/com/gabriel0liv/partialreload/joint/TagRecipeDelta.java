package com.gabriel0liv.partialreload.joint;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record TagRecipeDelta(Set<ResourceLocation> tagFilesChanged, Set<ResourceLocation> tagsChanged,
                             Set<String> tagMembersAdded, Set<String> tagMembersRemoved,
                             Set<ResourceLocation> recipesAdded, Set<ResourceLocation> recipesModified,
                             Set<ResourceLocation> recipesRemoved, Set<ResourceLocation> recipesImpactedByTagChanges,
                             Set<ResourceLocation> recipesRevalidatedWithoutJsonChange,
                             Set<ResourceLocation> recipesInvalidatedByTagChanges,
                             Set<ResourceLocation> serializerSafetyChanges) {
    public TagRecipeDelta {
        tagFilesChanged=Set.copyOf(tagFilesChanged); tagsChanged=Set.copyOf(tagsChanged);
        tagMembersAdded=Set.copyOf(tagMembersAdded); tagMembersRemoved=Set.copyOf(tagMembersRemoved);
        recipesAdded=Set.copyOf(recipesAdded); recipesModified=Set.copyOf(recipesModified); recipesRemoved=Set.copyOf(recipesRemoved);
        recipesImpactedByTagChanges=Set.copyOf(recipesImpactedByTagChanges); recipesRevalidatedWithoutJsonChange=Set.copyOf(recipesRevalidatedWithoutJsonChange);
        recipesInvalidatedByTagChanges=Set.copyOf(recipesInvalidatedByTagChanges); serializerSafetyChanges=Set.copyOf(serializerSafetyChanges);
    }
}
