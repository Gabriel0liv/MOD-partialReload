package com.gabriel0liv.partialreload.advancement;

import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import java.util.Map;
import java.util.Set;

public record AdvancementDependencySnapshot(int lootManagerIdentity,
        ActiveLootDataGeneration lootGeneration, int recipeManagerIdentity,
        Map<ResourceLocation, Recipe<?>> recipes, int functionManagerIdentity, int functionLibraryIdentity,
        Set<ResourceLocation> functionIds, int registryAccessIdentity,
        String activeResourceFingerprint) {
    public AdvancementDependencySnapshot {
        recipes=Map.copyOf(recipes); functionIds=Set.copyOf(functionIds);
    }
}
