package com.gabriel0liv.partialreload.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/** Deterministic logical snapshot used by rollback verification. */
public record ActiveRecipeSnapshot(ResourceLocation id, ResourceLocation serializerId,
                                   ResourceLocation recipeTypeId, ResourceLocation resultItemId,
                                   int resultCount, String stableFingerprint) {
    public static ActiveRecipeSnapshot capture(Recipe<?> recipe, RegistryAccess access) {
        ItemStack result = recipe.getResultItem(access);
        ResourceLocation serializer = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                .getKey(recipe.getSerializer());
        ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE
                .getKey(recipe.getType());
        ResourceLocation item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
        String stable = recipe.getId() + "|" + serializer + "|" + type + "|" + item + "|" + result.getCount();
        return new ActiveRecipeSnapshot(recipe.getId(), serializer, type, item, result.getCount(), stable);
    }
}
