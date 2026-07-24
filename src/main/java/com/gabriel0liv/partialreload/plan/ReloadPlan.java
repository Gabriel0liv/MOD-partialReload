package com.gabriel0liv.partialreload.plan;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ResourceChange;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ReloadPlan(
        UUID id,
        Instant createdAt,
        Set<ReloadCategory> categories,
        Set<ResourceLocation> providers,
        List<ResourceChange> changedResources,
        ReloadRisk risk,
        Set<String> dependencies,
        List<String> warnings,
        List<String> blockers,
        SupportStatus supportStatus,
        ApplySupport applySupport
) {
    public ReloadPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        providers = Set.copyOf(Objects.requireNonNull(providers, "providers"));
        changedResources = List.copyOf(Objects.requireNonNull(changedResources, "changedResources"));
        Objects.requireNonNull(risk, "risk");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        Objects.requireNonNull(supportStatus, "supportStatus");
        Objects.requireNonNull(applySupport, "applySupport");
    }
}
