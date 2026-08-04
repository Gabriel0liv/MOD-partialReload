package com.gabriel0liv.partialreload.glm;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.loot.IGlobalLootModifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ActiveGlobalLootModifierGeneration(
        Map<ResourceLocation, IGlobalLootModifier> orderedModifiers,
        UUID generationId,
        String diagnosticDigest
) {
    public ActiveGlobalLootModifierGeneration {
        orderedModifiers = Collections.unmodifiableMap(new LinkedHashMap<>(orderedModifiers));
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(diagnosticDigest, "diagnosticDigest");
    }
}
