package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeSerializerSafetyTest {
    @Test
    void knownVanillaSerializerIsExplicitlyClassified() {
        var result = RecipeSerializerSafetyClassifier.classify(
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_shapeless"), null);
        assertEquals(RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY, result.safety());
        assertTrue(result.source().contains("Ingredient.TagValue"));
    }

    @Test
    void unknownSerializerUsesConservativeFallback() {
        var result = RecipeSerializerSafetyClassifier.classify(
                ResourceLocation.fromNamespaceAndPath("example", "custom"), null);
        assertEquals(RecipeSerializerTagSafety.UNKNOWN_TAG_BEHAVIOR, result.safety());
    }
}
