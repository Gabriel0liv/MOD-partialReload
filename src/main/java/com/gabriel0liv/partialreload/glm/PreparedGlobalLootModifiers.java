package com.gabriel0liv.partialreload.glm;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.loot.IGlobalLootModifier;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PreparedGlobalLootModifiers implements PreparedReloadArtifact {
    private final UUID preparationId;
    private final Instant createdAt;
    private final ResourceSnapshot sourceSnapshot;
    private final List<ResourceLocation> orderedIds;
    private final Map<ResourceLocation, IGlobalLootModifier> modifiers;
    private final Map<ResourceLocation, GlobalLootModifierEntryEvidence> evidence;
    private final GlobalLootModifierDelta delta;
    private final ValidationReport validation;

    public PreparedGlobalLootModifiers(UUID preparationId, Instant createdAt,
                                       ResourceSnapshot sourceSnapshot,
                                       List<ResourceLocation> orderedIds,
                                       Map<ResourceLocation, IGlobalLootModifier> modifiers,
                                       Map<ResourceLocation, GlobalLootModifierEntryEvidence> evidence,
                                       GlobalLootModifierDelta delta,
                                       ValidationReport validation) {
        this.preparationId = preparationId;
        this.createdAt = createdAt;
        this.sourceSnapshot = sourceSnapshot;
        this.orderedIds = List.copyOf(orderedIds);
        this.modifiers = Collections.unmodifiableMap(new LinkedHashMap<>(modifiers));
        this.evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        this.delta = delta;
        this.validation = validation;
        if (!this.validation.hasErrorsOrBlockers()
                && !this.orderedIds.equals(List.copyOf(this.modifiers.keySet()))) {
            throw new IllegalArgumentException("GLM_CANDIDATE_ORDER_MISMATCH");
        }
    }

    @Override public UUID preparationId() { return preparationId; }
    @Override public ReloadCategory category() { return ReloadCategory.GLOBAL_LOOT_MODIFIERS; }
    @Override public Instant createdAt() { return createdAt; }
    @Override public ResourceSnapshot sourceSnapshot() { return sourceSnapshot; }
    @Override public ValidationReport validation() { return validation; }
    public List<ResourceLocation> orderedIds() { return orderedIds; }
    public Map<ResourceLocation, IGlobalLootModifier> modifiers() { return modifiers; }
    public Map<ResourceLocation, GlobalLootModifierEntryEvidence> evidence() { return evidence; }
    public GlobalLootModifierDelta delta() { return delta; }
}
