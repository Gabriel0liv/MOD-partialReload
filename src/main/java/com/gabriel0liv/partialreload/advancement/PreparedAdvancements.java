package com.gabriel0liv.partialreload.advancement;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.resources.ResourceLocation;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PreparedAdvancements(UUID preparationId, Instant createdAt,
        ResourceSnapshot sourceSnapshot, Map<ResourceLocation,Advancement> advancements,
        AdvancementList candidateList, AdvancementListSnapshot tree,
        Map<ResourceLocation,AdvancementResourceStack> resourceStacks,
        AdvancementDelta delta, AdvancementDependencySnapshot dependencies,
        ValidationReport validation) implements PreparedReloadArtifact {
    public PreparedAdvancements { advancements=Map.copyOf(advancements); resourceStacks=Map.copyOf(resourceStacks); }
    @Override public ReloadCategory category(){ return ReloadCategory.ADVANCEMENTS; }
}
