package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import java.util.Map;

/** Explicit compatibility table; unknown serializers fail closed only when tags matter. */
public final class RecipeSerializerSafetyClassifier {
    private static final Map<String, RecipeSerializerTagSafety> KNOWN = Map.ofEntries(
            Map.entry("minecraft:crafting_shaped", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:crafting_shapeless", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:smelting", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:blasting", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:smoking", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:campfire_cooking", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:stonecutting", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:smithing", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:smithing_transform", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY),
            Map.entry("minecraft:smithing_trim", RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY));

    private RecipeSerializerSafetyClassifier() { }

    public static RecipeSerializerSafetyClassification classify(ResourceLocation id, RecipeSerializer<?> serializer) {
        RecipeSerializerTagSafety safety = KNOWN.getOrDefault(id.toString(), RecipeSerializerTagSafety.UNKNOWN_TAG_BEHAVIOR);
        String source = KNOWN.containsKey(id.toString()) ? "Minecraft 1.20.1 Ingredient.TagValue source investigation" : "conservative fallback: serializer not explicitly classified";
        return new RecipeSerializerSafetyClassification(id, safety, source);
    }
}
