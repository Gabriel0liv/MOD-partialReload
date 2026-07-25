package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import java.util.Set;

public record PreparedRecipe(ResourceLocation id, String logicalPath, String winningPack,
                             String contentHash, ResourceLocation serializerId,
                             ResourceLocation recipeTypeId, Recipe<?> recipe,
                             Set<ResourceLocation> referencedItems,
                             Set<ResourceLocation> referencedTags) {
    public PreparedRecipe {
        referencedItems = Set.copyOf(referencedItems);
        referencedTags = Set.copyOf(referencedTags);
    }
}
