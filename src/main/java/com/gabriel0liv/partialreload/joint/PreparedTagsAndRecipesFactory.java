package com.gabriel0liv.partialreload.joint;

import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.recipe.RecipeSerializerTagSafety;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PreparedTagsAndRecipesFactory {
    private PreparedTagsAndRecipesFactory() {
    }

    public static PreparedTagsAndRecipes combine(UUID id, ResourceSnapshot snapshot,
                                                  PreparedTags tags, PreparedRecipes recipes) {
        List<ValidationIssue> issues = new ArrayList<>(tags.validation().issues());
        issues.addAll(recipes.validation().issues());

        Map<ResourceLocation, Set<ResourceLocation>> recipeToTags =
                new LinkedHashMap<>(recipes.dependencyGraph().dependencies());
        Map<ResourceLocation, Set<ResourceLocation>> tagToRecipes = new LinkedHashMap<>();
        recipeToTags.forEach((recipe, tagIds) -> tagIds.forEach(tag ->
                tagToRecipes.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(recipe)));

        Set<ResourceLocation> changedTags = new LinkedHashSet<>(tags.delta().tagsAdded());
        changedTags.addAll(tags.delta().tagsModified());
        changedTags.addAll(tags.delta().tagsRemoved());
        Set<ResourceLocation> impactedRecipes = new LinkedHashSet<>();
        recipeToTags.forEach((recipe, tagIds) -> {
            if (!Collections.disjoint(tagIds, changedTags)) {
                impactedRecipes.add(recipe);
            }
        });

        TagRecipeDependencyGraph graph = new TagRecipeDependencyGraph(
                recipeToTags, tagToRecipes, recipes.revalidatedDueToTagChange(),
                recipes.invalidatedByTagChange());
        TagRecipeDelta delta = new TagRecipeDelta(
                Set.copyOf(changedTags), Set.copyOf(changedTags), tags.delta().membersAdded(),
                tags.delta().membersRemoved(), recipes.delta().added(), recipes.delta().modified(),
                recipes.delta().removed(), impactedRecipes, recipes.revalidatedDueToTagChange(),
                recipes.invalidatedByTagChange(), recipes.serializerSafety().entrySet().stream()
                        .filter(entry -> entry.getValue() != RecipeSerializerTagSafety.TAG_INDEPENDENT_DURING_PARSE
                                && entry.getValue() != RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toUnmodifiableSet()));
        return new PreparedTagsAndRecipes(id, Instant.now(), snapshot, tags, recipes, graph, delta,
                new ValidationReport(issues));
    }
}
