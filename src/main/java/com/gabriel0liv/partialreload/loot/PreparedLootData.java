package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PreparedLootData implements PreparedReloadArtifact {
    public static final Set<ReloadCategory> COMPLETE_SCOPE =
            Set.of(ReloadCategory.PREDICATES, ReloadCategory.ITEM_MODIFIERS, ReloadCategory.LOOT);

    private final UUID preparationId;
    private final Instant createdAt;
    private final ResourceSnapshot sourceSnapshot;
    private final Set<ReloadCategory> requestedCategories;
    private final Map<ResourceLocation, PreparedPredicate> predicates;
    private final Map<ResourceLocation, PreparedItemModifier> itemModifiers;
    private final Map<ResourceLocation, PreparedLootTable> lootTables;
    private final LootDependencyGraph dependencyGraph;
    private final LootDataDelta delta;
    private final ValidationReport validation;

    public PreparedLootData(
            UUID preparationId,
            Instant createdAt,
            ResourceSnapshot sourceSnapshot,
            Set<ReloadCategory> requestedCategories,
            Map<ResourceLocation, PreparedPredicate> predicates,
            Map<ResourceLocation, PreparedItemModifier> itemModifiers,
            Map<ResourceLocation, PreparedLootTable> lootTables,
            LootDependencyGraph dependencyGraph,
            LootDataDelta delta,
            ValidationReport validation
    ) {
        this.preparationId = Objects.requireNonNull(preparationId, "preparationId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        this.requestedCategories = Set.copyOf(requestedCategories);
        if (this.requestedCategories.isEmpty() || !COMPLETE_SCOPE.containsAll(this.requestedCategories)) {
            throw new IllegalArgumentException("requestedCategories must contain loot data categories only");
        }
        this.predicates = Map.copyOf(predicates);
        this.itemModifiers = Map.copyOf(itemModifiers);
        this.lootTables = Map.copyOf(lootTables);
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph");
        this.delta = Objects.requireNonNull(delta, "delta");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    @Override
    public UUID preparationId() {
        return preparationId;
    }

    @Override
    public ReloadCategory category() {
        return requestedCategories.stream().min(Comparator.comparing(Enum::name)).orElseThrow();
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public ResourceSnapshot sourceSnapshot() {
        return sourceSnapshot;
    }

    public Set<ReloadCategory> requestedCategories() {
        return requestedCategories;
    }

    public Set<ReloadCategory> expandedCategories() {
        return COMPLETE_SCOPE;
    }

    public Map<ResourceLocation, PreparedPredicate> predicates() {
        return predicates;
    }

    public Map<ResourceLocation, PreparedItemModifier> itemModifiers() {
        return itemModifiers;
    }

    public Map<ResourceLocation, PreparedLootTable> lootTables() {
        return lootTables;
    }

    public LootDependencyGraph dependencyGraph() {
        return dependencyGraph;
    }

    public LootDataDelta delta() {
        return delta;
    }

    @Override
    public ValidationReport validation() {
        return validation;
    }
}

