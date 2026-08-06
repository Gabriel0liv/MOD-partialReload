package com.gabriel0liv.partialreload.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.UUID;

public record ActiveAdvancementGeneration(AdvancementList list,
        Map<ResourceLocation, Advancement> advancements, AdvancementListSnapshot tree,
        UUID generationId, String diagnosticDigest) {
    public ActiveAdvancementGeneration { advancements=Map.copyOf(advancements); }
}
