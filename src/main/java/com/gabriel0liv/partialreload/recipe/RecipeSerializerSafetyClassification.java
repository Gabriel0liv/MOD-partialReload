package com.gabriel0liv.partialreload.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

public record RecipeSerializerSafetyClassification(ResourceLocation serializerId,
                                                    RecipeSerializerTagSafety safety,
                                                    String source) { }
