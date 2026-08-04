package com.gabriel0liv.partialreload.glm;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.loot.PreparedLootData;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public record PreparedLootAndGlobalModifiers(
        UUID preparationId,
        Instant createdAt,
        ResourceSnapshot sourceSnapshot,
        PreparedLootData lootData,
        PreparedGlobalLootModifiers globalLootModifiers,
        ValidationReport validation
) implements PreparedReloadArtifact {
    public PreparedLootAndGlobalModifiers {
        Objects.requireNonNull(preparationId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(sourceSnapshot);
        Objects.requireNonNull(lootData);
        Objects.requireNonNull(globalLootModifiers);
        if (lootData.sourceSnapshot() != sourceSnapshot || globalLootModifiers.sourceSnapshot() != sourceSnapshot) {
            throw new IllegalArgumentException("LOOT_GLM_SNAPSHOT_MISMATCH");
        }
        ArrayList<com.gabriel0liv.partialreload.validation.ValidationIssue> issues = new ArrayList<>();
        issues.addAll(lootData.validation().issues());
        issues.addAll(globalLootModifiers.validation().issues());
        validation = validation == null ? new ValidationReport(issues) : validation;
    }

    @Override public ReloadCategory category() { return ReloadCategory.LOOT; }
}
