package com.gabriel0liv.partialreload.plan;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ResourceChange;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ProviderPlan(
        ResourceLocation providerId,
        Set<ReloadCategory> categories,
        List<ResourceChange> changes,
        ReloadRisk risk,
        Set<String> dependencies,
        List<String> warnings,
        List<String> blockers,
        SupportStatus supportStatus
) {
    public ProviderPlan {
        Objects.requireNonNull(providerId, "providerId");
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        Objects.requireNonNull(risk, "risk");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        Objects.requireNonNull(supportStatus, "supportStatus");
    }
}
