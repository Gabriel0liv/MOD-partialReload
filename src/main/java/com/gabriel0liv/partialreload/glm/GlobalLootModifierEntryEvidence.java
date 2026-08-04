package com.gabriel0liv.partialreload.glm;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record GlobalLootModifierEntryEvidence(
        ResourceLocation id,
        String listSourcePack,
        int finalPosition,
        String winningModifierPack,
        String winningHash,
        List<String> overrideStack
) {
    public GlobalLootModifierEntryEvidence {
        overrideStack = List.copyOf(overrideStack);
    }
}
