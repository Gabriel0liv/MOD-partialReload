package com.gabriel0liv.partialreload.plan;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.change.ResourceChange;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ReloadPlanner {
    public static final ResourceLocation VANILLA_PROVIDER_ID =
            ResourceLocation.fromNamespaceAndPath("partialreload", "vanilla_datapack");

    private final Clock clock;
    private final Supplier<UUID> idSupplier;

    public ReloadPlanner(Clock clock, Supplier<UUID> idSupplier) {
        this.clock = clock;
        this.idSupplier = idSupplier;
    }

    public ReloadPlan createPlan(ChangeSet input) {
        List<ResourceChange> changes = input.changedResources();
        Set<ReloadCategory> categories = EnumSet.noneOf(ReloadCategory.class);
        changes.forEach(change -> categories.add(change.category()));
        Set<ResourceLocation> providers = new HashSet<>();
        providers.add(VANILLA_PROVIDER_ID);

        ReloadRisk risk = ReloadRisk.LOW;
        SupportStatus support = SupportStatus.SUPPORTED_READ_ONLY;
        Set<String> dependencies = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        blockers.add("APPLY_NOT_IMPLEMENTED: commit is not implemented");

        for (ReloadCategory category : categories) {
            switch (category) {
                case DYNAMIC_REGISTRIES -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.RESTART_REQUIRED);
                    support = SupportStatus.RESTART_REQUIRED;
                    blockers.add("RESTART_REQUIRED: dynamic registries cannot be applied safely");
                }
                case UNKNOWN -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.UNKNOWN);
                    if (support == SupportStatus.SUPPORTED_READ_ONLY) support = SupportStatus.UNKNOWN;
                    blockers.add("UNKNOWN_RESOURCE: no provider contract exists");
                }
                case ORIGINS, KUBEJS, SILENTGEAR -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.HIGH);
                    if (support == SupportStatus.SUPPORTED_READ_ONLY) support = SupportStatus.PLANNED;
                    blockers.add("PROVIDER_PLANNED: " + category.commandName() + " integration is not implemented");
                }
                case FUNCTIONS -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.MODERATE);
                    dependencies.add("function tags and command dispatcher");
                    support = SupportStatus.PREPARE_SUPPORTED;
                    providers.add(ResourceLocation.fromNamespaceAndPath(
                            "partialreload", "vanilla_functions"
                    ));
                }
                case RECIPES -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.MODERATE);
                    dependencies.add("tags, recipe conditions and client synchronization");
                }
                case ADVANCEMENTS -> {
                    risk = ReloadRisk.max(risk, ReloadRisk.MODERATE);
                    dependencies.add("loot data and player advancement synchronization");
                }
                case PREDICATES, LOOT, ITEM_MODIFIERS -> {
                    dependencies.add("shared LootDataManager validation graph");
                    risk = ReloadRisk.max(risk, ReloadRisk.MODERATE);
                    support = SupportStatus.PREPARE_SUPPORTED;
                    providers.add(ResourceLocation.fromNamespaceAndPath(
                            "partialreload", "vanilla_loot_data"
                    ));
                    warnings.add("LOOT_CATEGORY_SCOPE_EXPANDED: prepare predicates, item modifiers and loot together");
                }
                case TAGS -> dependencies.add("registry tag binding, Forge events and client synchronization");
            }
        }

        if (changes.isEmpty()) {
            warnings.add("No changed resources match this plan");
        }

        return new ReloadPlan(
                idSupplier.get(),
                Instant.now(clock),
                categories,
                providers,
                changes,
                risk,
                dependencies,
                warnings,
                blockers,
                support,
                ApplySupport.APPLY_NOT_IMPLEMENTED
        );
    }
}
