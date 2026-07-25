package com.gabriel0liv.partialreload.recipe;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreparedRecipesTest {
    @Test
    void artifactCopiesCollectionsAndKeepsSnapshotIdentity() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "recipe");
        PreparedRecipe recipe = new PreparedRecipe(id, "recipes/recipe.json", "pack", "a".repeat(64),
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_shaped"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"), null,
                Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "stick")), Set.of());
        Map<ResourceLocation, PreparedRecipe> source = new java.util.HashMap<>();
        source.put(id, recipe);
        ResourceSnapshot snapshot = new ResourceSnapshot(Instant.now(), Map.of());
        PreparedRecipes artifact = new PreparedRecipes(UUID.randomUUID(), Instant.now(), snapshot, source,
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"), List.of(recipe)),
                new RecipeDependencyGraph(Map.of(id, Set.of())),
                new RecipeDelta(Set.of(id), Set.of(), Set.of(), Set.of()), ValidationReport.VALID,
                1, 1, 0, Set.of(recipe.serializerId()), Set.of(recipe.recipeTypeId()));

        source.clear();
        assertSame(snapshot, artifact.sourceSnapshot());
        assertEquals(recipe, artifact.recipesById().get(id));
        assertThrows(UnsupportedOperationException.class, () -> artifact.recipesById().clear());
        assertThrows(UnsupportedOperationException.class, () -> artifact.recipesByType().get(recipe.recipeTypeId()).clear());
        assertTrue(artifact.isApplicable());
    }
}
