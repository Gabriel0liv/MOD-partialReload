package com.gabriel0liv.partialreload.joint;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class PreparedTagsAndRecipes implements PreparedReloadArtifact {
    private final UUID preparationId; private final Instant createdAt; private final ResourceSnapshot snapshot;
    private final PreparedTags tags; private final PreparedRecipes recipes;
    private final TagRecipeDependencyGraph graph; private final TagRecipeDelta delta; private final ValidationReport validation;
    public PreparedTagsAndRecipes(UUID id, Instant createdAt, ResourceSnapshot snapshot, PreparedTags tags, PreparedRecipes recipes,
                                  TagRecipeDependencyGraph graph, TagRecipeDelta delta, ValidationReport validation) {
        if (tags.sourceSnapshot() != snapshot || recipes.sourceSnapshot() != snapshot) throw new IllegalArgumentException("JOINT_PREPARATION_SNAPSHOT_MISMATCH");
        this.preparationId=id; this.createdAt=createdAt; this.snapshot=snapshot; this.tags=tags; this.recipes=recipes; this.graph=graph; this.delta=delta; this.validation=validation;
    }
    public UUID preparationId(){return preparationId;} public Instant createdAt(){return createdAt;} public ResourceSnapshot sourceSnapshot(){return snapshot;}
    public ReloadCategory category(){return ReloadCategory.TAGS;}
    public Set<ReloadCategory> categories(){return Set.of(ReloadCategory.TAGS, ReloadCategory.RECIPES);}
    public PreparedTags preparedTags(){return tags;} public PreparedRecipes preparedRecipes(){return recipes;}
    public TagRecipeDependencyGraph dependencyGraph(){return graph;} public TagRecipeDelta delta(){return delta;} public ValidationReport validation(){return validation;}
    @Override public boolean isApplicable(){return !validation.hasErrorsOrBlockers() && tags.isApplicable() && recipes.isApplicable();}
}
