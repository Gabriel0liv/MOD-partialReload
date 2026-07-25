package com.gabriel0liv.partialreload.recipe;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PreparedRecipes implements PreparedReloadArtifact {
    private final UUID preparationId; private final Instant createdAt; private final ResourceSnapshot snapshot;
    private final Map<ResourceLocation, PreparedRecipe> byId;
    private final Map<ResourceLocation, List<PreparedRecipe>> byType;
    private final RecipeDependencyGraph graph; private final RecipeDelta delta;
    private final ValidationReport validation; private final int discovered, prepared, skipped;
    private final Set<ResourceLocation> serializers, types;
    private final Set<ResourceLocation> revalidatedDueToTagChange;
    private final Set<ResourceLocation> conditionBearingRecipes;
    private final Set<ResourceLocation> impactedByTagChanges;
    private final Set<ResourceLocation> invalidatedByTagChange;
    private final Map<ResourceLocation, RecipeSerializerTagSafety> serializerSafety;
    private final Map<ResourceLocation, String> serializerSafetySources;
    private final Map<ResourceLocation, String> conditionClassifications;

    public PreparedRecipes(UUID id, Instant createdAt, ResourceSnapshot snapshot,
                           Map<ResourceLocation, PreparedRecipe> byId,
                           Map<ResourceLocation, List<PreparedRecipe>> byType,
                           RecipeDependencyGraph graph, RecipeDelta delta,
                           ValidationReport validation, int discovered, int prepared, int skipped,
                           Set<ResourceLocation> serializers, Set<ResourceLocation> types) {
        this(id, createdAt, snapshot, byId, byType, graph, delta, validation, discovered, prepared, skipped, serializers, types, Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }
    public PreparedRecipes(UUID id, Instant createdAt, ResourceSnapshot snapshot,
                           Map<ResourceLocation, PreparedRecipe> byId,
                           Map<ResourceLocation, List<PreparedRecipe>> byType,
                           RecipeDependencyGraph graph, RecipeDelta delta,
                           ValidationReport validation, int discovered, int prepared, int skipped,
                           Set<ResourceLocation> serializers, Set<ResourceLocation> types,
                           Set<ResourceLocation> revalidatedDueToTagChange) {
        this(id, createdAt, snapshot, byId, byType, graph, delta, validation, discovered, prepared, skipped, serializers, types, revalidatedDueToTagChange, Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }
    public PreparedRecipes(UUID id, Instant createdAt, ResourceSnapshot snapshot,
                           Map<ResourceLocation, PreparedRecipe> byId,
                           Map<ResourceLocation, List<PreparedRecipe>> byType,
                           RecipeDependencyGraph graph, RecipeDelta delta,
                           ValidationReport validation, int discovered, int prepared, int skipped,
                           Set<ResourceLocation> serializers, Set<ResourceLocation> types,
                           Set<ResourceLocation> revalidatedDueToTagChange,
                           Set<ResourceLocation> conditionBearingRecipes) {
        this(id, createdAt, snapshot, byId, byType, graph, delta, validation, discovered, prepared, skipped, serializers, types,
                revalidatedDueToTagChange, conditionBearingRecipes, Set.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }
    public PreparedRecipes(UUID id, Instant createdAt, ResourceSnapshot snapshot,
                           Map<ResourceLocation, PreparedRecipe> byId,
                           Map<ResourceLocation, List<PreparedRecipe>> byType,
                           RecipeDependencyGraph graph, RecipeDelta delta,
                           ValidationReport validation, int discovered, int prepared, int skipped,
                           Set<ResourceLocation> serializers, Set<ResourceLocation> types,
                           Set<ResourceLocation> revalidatedDueToTagChange,
                           Set<ResourceLocation> conditionBearingRecipes,
                           Set<ResourceLocation> impactedByTagChanges,
                           Set<ResourceLocation> invalidatedByTagChange,
                           Map<ResourceLocation, RecipeSerializerTagSafety> serializerSafety,
                           Map<ResourceLocation, String> serializerSafetySources,
                           Map<ResourceLocation, String> conditionClassifications) {
        this.preparationId=id; this.createdAt=createdAt; this.snapshot=snapshot;
        this.byId=Map.copyOf(byId); this.byType=byType.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> List.copyOf(e.getValue()))); this.graph=graph; this.delta=delta;
        this.validation=validation; this.discovered=discovered; this.prepared=prepared; this.skipped=skipped;
        this.serializers=Set.copyOf(serializers); this.types=Set.copyOf(types);
        this.revalidatedDueToTagChange=Set.copyOf(revalidatedDueToTagChange);
        this.conditionBearingRecipes=Set.copyOf(conditionBearingRecipes);
        this.impactedByTagChanges=Set.copyOf(impactedByTagChanges);
        this.invalidatedByTagChange=Set.copyOf(invalidatedByTagChange);
        this.serializerSafety=Map.copyOf(serializerSafety);
        this.serializerSafetySources=Map.copyOf(serializerSafetySources);
        this.conditionClassifications=Map.copyOf(conditionClassifications);
    }
    public UUID preparationId(){return preparationId;} public ReloadCategory category(){return ReloadCategory.RECIPES;}
    public Instant createdAt(){return createdAt;} public ResourceSnapshot sourceSnapshot(){return snapshot;}
    public Map<ResourceLocation, PreparedRecipe> recipesById(){return byId;}
    public Map<ResourceLocation, List<PreparedRecipe>> recipesByType(){return byType;}
    public RecipeDependencyGraph dependencyGraph(){return graph;} public RecipeDelta delta(){return delta;}
    public ValidationReport validation(){return validation;} public int discoveredRecipes(){return discovered;}
    public int preparedRecipes(){return prepared;} public int skippedByCondition(){return skipped;}
    public Set<ResourceLocation> serializersUsed(){return serializers;} public Set<ResourceLocation> recipeTypesUsed(){return types;}
    public Set<ResourceLocation> revalidatedDueToTagChange(){return revalidatedDueToTagChange;}
    public Set<ResourceLocation> conditionBearingRecipes(){return conditionBearingRecipes;}
    public Set<ResourceLocation> recipesImpactedByTagChanges(){return impactedByTagChanges;}
    public Set<ResourceLocation> invalidatedByTagChange(){return invalidatedByTagChange;}
    public Map<ResourceLocation, RecipeSerializerTagSafety> serializerSafety(){return serializerSafety;}
    public Map<ResourceLocation, String> serializerSafetySources(){return serializerSafetySources;}
    public Map<ResourceLocation, String> conditionClassifications(){return conditionClassifications;}
}
