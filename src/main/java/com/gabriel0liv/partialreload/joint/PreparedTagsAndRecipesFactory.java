package com.gabriel0liv.partialreload.joint;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.recipe.RecipeSerializerTagSafety;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import java.time.Instant;
import java.util.*;
public final class PreparedTagsAndRecipesFactory {
    private PreparedTagsAndRecipesFactory() {}
    public static PreparedTagsAndRecipes combine(UUID id, com.gabriel0liv.partialreload.resource.ResourceSnapshot snapshot, PreparedTags tags, PreparedRecipes recipes) {
        List<ValidationIssue> issues = new ArrayList<>(tags.validation().issues()); issues.addAll(recipes.validation().issues());
        Map<ResourceLocation, Set<ResourceLocation>> recipeToTags = new LinkedHashMap<>(recipes.dependencyGraph().dependencies());
        Map<ResourceLocation, Set<ResourceLocation>> tagToRecipes = new LinkedHashMap<>();
        recipeToTags.forEach((recipe, ids) -> ids.forEach(tag -> tagToRecipes.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(recipe)));
        Set<ResourceLocation> changed = new LinkedHashSet<>(tags.delta().tagsAdded()); changed.addAll(tags.delta().tagsModified()); changed.addAll(tags.delta().tagsRemoved());
        Set<ResourceLocation> impacted = new LinkedHashSet<>(); recipeToTags.forEach((recipe, ids) -> { if (!Collections.disjoint(ids, changed)) impacted.add(recipe); });
        TagRecipeDependencyGraph graph = new TagRecipeDependencyGraph(recipeToTags, tagToRecipes, recipes.revalidatedDueToTagChange(), recipes.invalidatedByTagChange());
        TagRecipeDelta delta = new TagRecipeDelta(Set.copyOf(changed), Set.copyOf(changed), tags.delta().membersAdded(), tags.delta().membersRemoved(), recipes.delta().added(), recipes.delta().modified(), recipes.delta().removed(), impacted, recipes.revalidatedDueToTagChange(), recipes.invalidatedByTagChange(), recipes.serializerSafety().entrySet().stream().filter(e -> e.getValue() != RecipeSerializerTagSafety.TAG_INDEPENDENT_DURING_PARSE && e.getValue() != RecipeSerializerTagSafety.STORES_TAG_KEY_ONLY).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        return new PreparedTagsAndRecipes(id, Instant.now(), snapshot, tags, recipes, graph, delta, new ValidationReport(issues));
    }
}
