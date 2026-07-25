package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record RecipeDelta(Set<ResourceLocation> added, Set<ResourceLocation> modified,
                          Set<ResourceLocation> removed, Set<ResourceLocation> restoredFromLowerPack) {
    public RecipeDelta {
        added = Set.copyOf(added); modified = Set.copyOf(modified);
        removed = Set.copyOf(removed); restoredFromLowerPack = Set.copyOf(restoredFromLowerPack);
    }
}
