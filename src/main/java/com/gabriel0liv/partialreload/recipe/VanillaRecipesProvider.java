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
import java.io.InputStreamReader; import java.nio.charset.StandardCharsets; import java.time.Instant;
import java.util.*;

public final class VanillaRecipesProvider {
    public PreparedRecipes prepare(ResourceManager manager, ResourceSnapshot snapshot,
                                   ResourceSnapshot baseline, Set<ResourceLocation> changedTags,
                                   int maxRecipes, long maxJsonBytes, long timeoutNanos, UUID id) {
        long deadline = System.nanoTime()+timeoutNanos; List<ValidationIssue> issues=new ArrayList<>();
        Map<ResourceLocation, PreparedRecipe> byId=new LinkedHashMap<>(); Map<ResourceLocation,List<PreparedRecipe>> byType=new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> deps=new LinkedHashMap<>(); Set<ResourceLocation> serializers=new LinkedHashSet<>(), types=new LinkedHashSet<>();
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
        return new PreparedRecipes(id,Instant.now(),snapshot,byId,byType,new RecipeDependencyGraph(deps),new RecipeDelta(added,Set.of(),removed,Set.of()),new ValidationReport(issues),resources.size(),byId.size(),skipped,serializers,types);
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
