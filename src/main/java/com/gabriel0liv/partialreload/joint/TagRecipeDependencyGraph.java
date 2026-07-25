package com.gabriel0liv.partialreload.joint;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record TagRecipeDependencyGraph(
        Map<ResourceLocation, Set<ResourceLocation>> recipeToTags,
        Map<ResourceLocation, Set<ResourceLocation>> tagToRecipes,
        Set<ResourceLocation> revalidatedRecipes,
        Set<ResourceLocation> invalidatedRecipes) {
    public TagRecipeDependencyGraph {
        recipeToTags = copy(recipeToTags); tagToRecipes = copy(tagToRecipes);
        revalidatedRecipes = Set.copyOf(revalidatedRecipes); invalidatedRecipes = Set.copyOf(invalidatedRecipes);
    }
    private static Map<ResourceLocation, Set<ResourceLocation>> copy(Map<ResourceLocation, Set<ResourceLocation>> value) {
        return value.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }
    public int edgeCount() { return recipeToTags.values().stream().mapToInt(Set::size).sum(); }
}
