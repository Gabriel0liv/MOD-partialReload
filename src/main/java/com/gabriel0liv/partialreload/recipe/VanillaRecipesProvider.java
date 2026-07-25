package com.gabriel0liv.partialreload.recipe;

import com.google.gson.*;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.*;
import com.gabriel0liv.partialreload.validation.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import com.gabriel0liv.partialreload.tags.CandidateTagResolutionView;
import java.io.InputStreamReader; import java.nio.charset.StandardCharsets; import java.time.Instant;
import java.util.*;

public final class VanillaRecipesProvider {
    public PreparedRecipes prepareWithCandidateTags(ResourceManager manager, ResourceSnapshot snapshot,
                                                     ResourceSnapshot baseline, CandidateTagResolutionView candidateTags,
                                                     int maxRecipes, long maxJsonBytes, long timeoutNanos, UUID id) {
        return prepareWithCandidateTags(manager, snapshot, baseline, candidateTags, Set.of(), maxRecipes, maxJsonBytes, timeoutNanos, id);
    }

    public PreparedRecipes prepareWithCandidateTags(ResourceManager manager, ResourceSnapshot snapshot,
                                                     ResourceSnapshot baseline, CandidateTagResolutionView candidateTags,
                                                     Set<ResourceLocation> changedTagIds,
                                                     int maxRecipes, long maxJsonBytes, long timeoutNanos, UUID id) {
        PreparedRecipes base = prepare(manager, snapshot, baseline, Set.of(), maxRecipes, maxJsonBytes, timeoutNanos, id);
        List<ValidationIssue> issues = new ArrayList<>(base.validation().issues());
        Set<ResourceLocation> revalidated = new LinkedHashSet<>();
        Set<ResourceLocation> invalidated = new LinkedHashSet<>();
        Set<ResourceLocation> impacted = new LinkedHashSet<>();
        Map<ResourceLocation, RecipeSerializerTagSafety> safety = new LinkedHashMap<>();
        Map<ResourceLocation, String> safetySources = new LinkedHashMap<>();
        Map<ResourceLocation, String> conditionClassifications = new LinkedHashMap<>();
        for (PreparedRecipe recipe : base.recipesById().values()) {
            var classification = RecipeSerializerSafetyClassifier.classify(recipe.serializerId(), recipe.recipe() == null ? null : recipe.recipe().getSerializer());
            safety.put(recipe.serializerId(), classification.safety()); safetySources.put(recipe.serializerId(), classification.source());
            boolean usesChangedTag = false;
            for (ResourceLocation tag : recipe.referencedTags()) {
                if (dependsOnChangedTag(candidateTags, tag, changedTagIds, new LinkedHashSet<>())) usesChangedTag = true;
                var result = candidateTags.resolve("items", tag);
                if (result.status() == com.gabriel0liv.partialreload.tags.TagResolutionStatus.TAG_MISSING || result.status() == com.gabriel0liv.partialreload.tags.TagResolutionStatus.REGISTRY_UNSUPPORTED) {
                    invalidated.add(recipe.id());
                    issues.add(issue(ValidationSeverity.BLOCKER, "RECIPE_CANDIDATE_TAG_MISSING", recipe.id(), "candidate item tag is missing: " + tag));
                } else if (result.status() == com.gabriel0liv.partialreload.tags.TagResolutionStatus.TAG_EMPTY) {
                    invalidated.add(recipe.id());
                    issues.add(issue(ValidationSeverity.BLOCKER, "RECIPE_CANDIDATE_TAG_EMPTY", recipe.id(), "candidate item tag is empty: " + tag));
                } else if (result.status() == com.gabriel0liv.partialreload.tags.TagResolutionStatus.CYCLE) {
                    invalidated.add(recipe.id());
                    issues.add(issue(ValidationSeverity.BLOCKER, "RECIPE_CANDIDATE_TAG_CYCLE", recipe.id(), "candidate item tag contains a cycle: " + tag));
                }
            }
            if (usesChangedTag) {
                impacted.add(recipe.id());
                boolean jsonChanged = recipeJsonChanged(snapshot, baseline, recipe);
                if (!jsonChanged) revalidated.add(recipe.id());
                if (classification.safety() == RecipeSerializerTagSafety.UNKNOWN_TAG_BEHAVIOR
                        || classification.safety() == RecipeSerializerTagSafety.READS_ACTIVE_TAG_MEMBERS
                        || classification.safety() == RecipeSerializerTagSafety.STORES_ACTIVE_HOLDER_SET) {
                    invalidated.add(recipe.id());
                    issues.add(issue(ValidationSeverity.BLOCKER, "RECIPE_SERIALIZER_CANDIDATE_TAGS_UNSUPPORTED", recipe.id(),
                            "serializer behavior with candidate tags is unknown or reads active members: " + recipe.serializerId()));
                }
                if (base.conditionBearingRecipes().contains(recipe.id())) {
                    conditionClassifications.put(recipe.id(), "CONDITION_TAG_BEHAVIOR_UNKNOWN");
                    invalidated.add(recipe.id());
                    issues.add(issue(ValidationSeverity.BLOCKER, "RECIPE_CONDITION_CANDIDATE_TAGS_UNSUPPORTED", recipe.id(), "condition behavior with candidate tags is unknown"));
                }
            } else if (base.conditionBearingRecipes().contains(recipe.id())) {
                conditionClassifications.put(recipe.id(), "CONDITION_TAG_INDEPENDENT");
            }
        }
        RecipeDependencyGraph graph = base.dependencyGraph();
        Map<ResourceLocation, Set<ResourceLocation>> recipeToTags = new LinkedHashMap<>(graph.dependencies());
        Map<ResourceLocation, Set<ResourceLocation>> tagToRecipes = new LinkedHashMap<>();
        recipeToTags.forEach((recipe, tags) -> tags.forEach(tag -> tagToRecipes.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(recipe)));
        return new PreparedRecipes(base.preparationId(), base.createdAt(), base.sourceSnapshot(), base.recipesById(), base.recipesByType(),
                graph, base.delta(), new ValidationReport(issues), base.discoveredRecipes(), base.preparedRecipes(), base.skippedByCondition(),
                base.serializersUsed(), base.recipeTypesUsed(), revalidated, base.conditionBearingRecipes(), impacted, invalidated,
                safety, safetySources, conditionClassifications);
    }

    private static boolean dependsOnChangedTag(CandidateTagResolutionView view, ResourceLocation tag,
                                                Set<ResourceLocation> changed, Set<ResourceLocation> visiting) {
        if (changed.contains(tag)) return true;
        if (!visiting.add(tag)) return false;
        for (ResourceLocation nested : view.referencedTags("items", tag)) {
            if (dependsOnChangedTag(view, nested, changed, visiting)) return true;
        }
        visiting.remove(tag); return false;
    }

    private static boolean recipeJsonChanged(ResourceSnapshot snapshot, ResourceSnapshot baseline, PreparedRecipe recipe) {
        if (baseline == null) return true;
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(recipe.id().getNamespace(), recipe.logicalPath());
        ResourceDescriptor current = snapshot.resources().get(location);
        ResourceDescriptor previous = baseline.resources().get(location);
        return current == null || previous == null || !current.fingerprint().hash().equals(previous.fingerprint().hash());
    }
    @SuppressWarnings("deprecation")
    public PreparedRecipes prepare(ResourceManager manager, ResourceSnapshot snapshot,
                                   ResourceSnapshot baseline, Set<ResourceLocation> changedTags,
                                   int maxRecipes, long maxJsonBytes, long timeoutNanos, UUID id) {
        long deadline = System.nanoTime()+timeoutNanos; List<ValidationIssue> issues=new ArrayList<>();
        Map<ResourceLocation, PreparedRecipe> byId=new LinkedHashMap<>(); Map<ResourceLocation,List<PreparedRecipe>> byType=new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> deps=new LinkedHashMap<>(); Set<ResourceLocation> serializers=new LinkedHashSet<>(), types=new LinkedHashSet<>();
        Set<ResourceLocation> conditionBearing = new LinkedHashSet<>();
        int skipped=0; long totalBytes=0; Map<ResourceLocation, Resource> resources=manager.listResources("recipes", l->l.getPath().endsWith(".json"));
        if(resources.size()>maxRecipes) issues.add(issue(ValidationSeverity.BLOCKER,"RECIPE_LIMIT_EXCEEDED",null,"recipe limit exceeded"));
        for(var entry: resources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList()) {
            if(System.nanoTime()>deadline){issues.add(issue(ValidationSeverity.BLOCKER,"RECIPE_PREPARATION_TIMEOUT",entry.getKey(),"recipe preparation timed out")); break;}
            ResourceLocation file=entry.getKey(), idLoc=ResourceScanner.logicalId(file, ReloadCategory.RECIPES);
            ResourceDescriptor descriptor = snapshot.resources().get(file);
            if (descriptor != null) {
                totalBytes += descriptor.fingerprint().size();
                if (totalBytes > maxJsonBytes) {
                    issues.add(issue(ValidationSeverity.BLOCKER,"RECIPE_LIMIT_EXCEEDED",idLoc,"recipe JSON byte limit exceeded"));
                    break;
                }
            }
            try(InputStreamReader reader=new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
                if(json.has("conditions") && !CraftingHelper.processConditions(json.getAsJsonArray("conditions"), ICondition.IContext.EMPTY)) {
                    skipped++; issues.add(issue(ValidationSeverity.INFO,"RECIPE_CONDITION_FALSE",idLoc,"condition evaluated false")); continue;
                }
                var recipe=RecipeManager.fromJson(idLoc,json,ICondition.IContext.EMPTY);
                if (json.has("conditions")) conditionBearing.add(idLoc);
                ResourceLocation sid=BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
                ResourceLocation tid=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                if(sid==null) sid=ResourceLocation.withDefaultNamespace("unknown"); if(tid==null) tid=ResourceLocation.withDefaultNamespace("unknown");
                String hash=snapshot.resources().get(file)==null?"":snapshot.resources().get(file).fingerprint().hash();
                Set<ResourceLocation> tags=new LinkedHashSet<>(), items=new LinkedHashSet<>(); collectDependencies(json,items,tags);
                if(!Collections.disjoint(tags,changedTags)) issues.add(issue(ValidationSeverity.BLOCKER,"RECIPE_TAG_DEPENDENCY_CHANGED",idLoc,"relevant item tag changed"));
                PreparedRecipe prepared=new PreparedRecipe(idLoc,file.getPath(),entry.getValue().sourcePackId(),hash,sid,tid,recipe,items,tags);
                byId.put(idLoc,prepared); byType.computeIfAbsent(tid,k->new ArrayList<>()).add(prepared); serializers.add(sid); types.add(tid); deps.put(idLoc,tags);
            } catch(JsonParseException|IllegalArgumentException ex){issues.add(issue(ValidationSeverity.ERROR,"RECIPE_DESERIALIZATION_ERROR",idLoc,ex.getMessage()));}
            catch(Exception ex){issues.add(issue(ValidationSeverity.ERROR,"RECIPE_JSON_SYNTAX_ERROR",idLoc,ex.getMessage()));}
        }
        Set<ResourceLocation> current=Set.copyOf(byId.keySet()), old=baseline==null?Set.of():baseline.resources().values().stream().filter(e->e.category()==ReloadCategory.RECIPES).map(ResourceDescriptor::logicalId).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> added=new HashSet<>(current); added.removeAll(old); Set<ResourceLocation> removed=new HashSet<>(old); removed.removeAll(current);
        return new PreparedRecipes(id,Instant.now(),snapshot,byId,byType,new RecipeDependencyGraph(deps),new RecipeDelta(added,Set.of(),removed,Set.of()),new ValidationReport(issues),resources.size(),byId.size(),skipped,serializers,types,Set.of(),conditionBearing);
    }
    private static void collectDependencies(JsonElement e, Set<ResourceLocation> items, Set<ResourceLocation> tags){
        if(e.isJsonObject()) for(var x:e.getAsJsonObject().entrySet()) {
            if(x.getValue().isJsonPrimitive() && x.getValue().getAsJsonPrimitive().isString()) {
                try { if (x.getKey().equals("tag")) tags.add(ResourceLocation.parse(x.getValue().getAsString()));
                      else if (x.getKey().equals("item")) items.add(ResourceLocation.parse(x.getValue().getAsString())); }
                catch (Exception ignored) { }
            }
            collectDependencies(x.getValue(),items,tags);
        } else if(e.isJsonArray()) e.getAsJsonArray().forEach(v->collectDependencies(v,items,tags));
    }
    private static ValidationIssue issue(ValidationSeverity s,String code,ResourceLocation id,String msg){return new ValidationIssue(s,code,ReloadCategory.RECIPES,null,id,null,msg,null,null);}
}
