package com.gabriel0liv.partialreload.advancement;

import com.gabriel0liv.partialreload.api.*;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.loot.LootDataManagerBridge;
import com.gabriel0liv.partialreload.function.FunctionLibraryBridge;
import com.gabriel0liv.partialreload.plan.*;
import com.gabriel0liv.partialreload.resource.*;
import com.gabriel0liv.partialreload.validation.*;
import com.google.gson.*;
import net.minecraft.advancements.*;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.loot.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Full, passive advancement candidate using the exact vanilla/Forge parser. */
public final class VanillaAdvancementProvider implements ReloadProvider {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("partialreload","vanilla_advancements");
    private final ResourceScanner scanner;
    public VanillaAdvancementProvider(ResourceScanner scanner){this.scanner=scanner;}
    @Override public ResourceLocation id(){return ID;}
    @Override public Set<ReloadCategory> categories(){return Set.of(ReloadCategory.ADVANCEMENTS);}
    @Override public ProviderCompatibility compatibility(ReloadEnvironment environment){return ProviderCompatibility.PREPARE_SUPPORTED;}
    @Override public ScanResult scan(ScanContext context)throws PartialReloadException{return new ScanResult(scanner.scan(context));}
    @Override public ValidationReport validate(com.gabriel0liv.partialreload.api.ValidationContext context,ChangeSet changes){return ValidationReport.VALID;}
    @Override public ProviderPlan createPlan(PlanningContext context,ChangeSet changes){
        ReloadPlan p=new ReloadPlanner(context.clock(),UUID::randomUUID).createPlan(changes.forCategory(ReloadCategory.ADVANCEMENTS));
        return new ProviderPlan(ID,p.categories(),p.changedResources(),p.risk(),Set.of("active loot, recipes, functions, registries and tags"),
                List.of("Connected players are rebound and synchronized through vanilla packets"),p.blockers(),p.supportStatus());
    }

    public PreparedAdvancements prepare(AdvancementPreparationContext context){
        long deadline=System.nanoTime()+context.timeout().toNanos();
        List<ValidationIssue> issues=new ArrayList<>();
        Map<ResourceLocation,Resource> winners=context.resourceManager().listResources("advancements",id->id.getPath().endsWith(".json"));
        if(winners.size()>context.maxAdvancements()) issues.add(issue(ValidationSeverity.BLOCKER,"ADVANCEMENT_LIMIT_EXCEEDED",null,"advancement limit exceeded"));
        Map<ResourceLocation,Advancement.Builder> builders=new LinkedHashMap<>();
        Map<ResourceLocation,JsonObject> jsonById=new LinkedHashMap<>();
        Map<ResourceLocation,AdvancementResourceStack> stacks=new LinkedHashMap<>();
        long bytes=0;
        ServerAdvancementManager active=context.server().getAdvancements();
        for(var entry:winners.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList()){
            if(System.nanoTime()>deadline){issues.add(issue(ValidationSeverity.BLOCKER,"ADVANCEMENT_PREPARATION_TIMEOUT",null,"advancement preparation timed out"));break;}
            ResourceLocation file=entry.getKey(), id=ResourceScanner.logicalId(file,ReloadCategory.ADVANCEMENTS);
            List<String> packs=new ArrayList<>(), hashes=new ArrayList<>(); byte[] winnerBytes=null;
            try{
                for(Resource resource:context.resourceManager().getResourceStack(file)){
                    byte[] raw; try(var in=resource.open()){raw=in.readAllBytes();}
                    bytes+=raw.length; packs.add(resource.sourcePackId()); hashes.add(ResourceFingerprint.sha256(raw).hash()); winnerBytes=raw;
                }
                if(bytes>context.maxJsonBytes())throw new IllegalStateException("ADVANCEMENT_LIMIT_EXCEEDED: JSON byte limit exceeded");
                JsonObject json=JsonParser.parseString(new String(Objects.requireNonNull(winnerBytes),StandardCharsets.UTF_8)).getAsJsonObject();
                Advancement.Builder builder=Advancement.Builder.fromJson(json,new DeserializationContext(id,active.lootData),active.context);
                if(builder!=null){builders.put(id,builder);jsonById.put(id,json);}
                String winnerPack=packs.isEmpty()?entry.getValue().sourcePackId():packs.get(packs.size()-1);
                String winnerHash=hashes.isEmpty()?"":hashes.get(hashes.size()-1);
                stacks.put(id,new AdvancementResourceStack(id,file.getPath(),winnerPack,winnerHash,packs,hashes));
            }catch(JsonParseException ex){issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_JSON_SYNTAX_ERROR",id,ex.getMessage()));}
            catch(Exception ex){issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_DESERIALIZATION_ERROR",id,message(ex)));}
        }
        validateParentGraph(jsonById,issues);
        AdvancementList list=new AdvancementList(); list.add(builders);
        for(Advancement root:list.getRoots())if(root.getDisplay()!=null)TreeNodePosition.run(root);
        Map<ResourceLocation,Advancement> candidate=new LinkedHashMap<>(); list.getAllAdvancements().forEach(a->candidate.put(a.getId(),a));
        for(ResourceLocation id:builders.keySet())if(!candidate.containsKey(id))issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_PARENT_MISSING",id,"advancement could not resolve its parent"));
        validateRewards(context.server(),candidate,issues);
        AdvancementDelta delta=delta(context, candidate, jsonById, stacks);
        return new PreparedAdvancements(context.idSupplier().get(),Instant.now(context.clock()),context.snapshot(),candidate,list,
                AdvancementListSnapshot.from(candidate.values()),stacks,delta,captureDependencies(context.server(),context.activeReference()),new ValidationReport(issues));
    }

    public static AdvancementDependencySnapshot captureDependencies(MinecraftServer server,ResourceSnapshot reference){
        Map<ResourceLocation,net.minecraft.world.item.crafting.Recipe<?>> recipes=new LinkedHashMap<>();
        server.getRecipeManager().getRecipes().forEach(r->recipes.put(r.getId(),r));
        Set<ResourceLocation> functions=new LinkedHashSet<>(); server.getFunctions().getFunctionNames().forEach(functions::add);
        return new AdvancementDependencySnapshot(System.identityHashCode(server.getLootData()),LootDataManagerBridge.capture(server.getLootData()),
                System.identityHashCode(server.getRecipeManager()),recipes,System.identityHashCode(server.getFunctions()),
                System.identityHashCode(FunctionLibraryBridge.activeLibrary(server.getFunctions())),functions,
                System.identityHashCode(server.registryAccess()),snapshotFingerprint(reference));
    }

    private static void validateRewards(MinecraftServer server,Map<ResourceLocation,Advancement> values,List<ValidationIssue> issues){
        for(Advancement advancement:values.values()){
            JsonElement rewards=advancement.getRewards().serializeToJson();
            if(advancement.getCriteria().isEmpty()&&!rewards.isJsonNull())issues.add(issue(ValidationSeverity.BLOCKER,"ADVANCEMENT_AUTOMATIC_REWARD_NOT_TRANSACTION_SAFE",advancement.getId(),"automatic advancement has non-compensable rewards"));
            if(!rewards.isJsonObject())continue; JsonObject object=rewards.getAsJsonObject();
            if(object.has("recipes"))for(JsonElement value:object.getAsJsonArray("recipes")){ResourceLocation id=ResourceLocation.parse(value.getAsString());if(server.getRecipeManager().byKey(id).isEmpty())issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_REWARD_RECIPE_MISSING",advancement.getId(),id.toString()));}
            if(object.has("loot"))for(JsonElement value:object.getAsJsonArray("loot")){ResourceLocation id=ResourceLocation.parse(value.getAsString());if(server.getLootData().getElement(new LootDataId<>(LootDataType.TABLE,id))==null)issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_REWARD_LOOT_MISSING",advancement.getId(),id.toString()));}
            if(object.has("function")){ResourceLocation id=ResourceLocation.parse(object.get("function").getAsString());if(server.getFunctions().get(id).isEmpty())issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_REWARD_FUNCTION_MISSING",advancement.getId(),id.toString()));}
        }
    }

    private static void validateParentGraph(Map<ResourceLocation,JsonObject> json,List<ValidationIssue> issues){
        Map<ResourceLocation,ResourceLocation> parents=new LinkedHashMap<>();
        json.forEach((id,value)->{if(value.has("parent")){try{parents.put(id,ResourceLocation.parse(value.get("parent").getAsString()));}catch(Exception ex){issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_PARENT_MISSING",id,message(ex)));}}});
        parents.forEach((id,parent)->{if(!json.containsKey(parent))issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_PARENT_MISSING",id,parent.toString()));});
        for(ResourceLocation id:parents.keySet()){Set<ResourceLocation> seen=new LinkedHashSet<>();ResourceLocation current=id;while(current!=null){if(!seen.add(current)){issues.add(issue(ValidationSeverity.ERROR,"ADVANCEMENT_PARENT_CYCLE",id,"cycle="+seen));break;}current=parents.get(current);}}
    }

    private static AdvancementDelta delta(AdvancementPreparationContext context,Map<ResourceLocation,Advancement> candidate,
            Map<ResourceLocation,JsonObject> json,Map<ResourceLocation,AdvancementResourceStack> stacks){
        Map<ResourceLocation,Advancement> old=new LinkedHashMap<>();context.server().getAdvancements().getAllAdvancements().forEach(a->old.put(a.getId(),a));
        Set<ResourceLocation> added=new LinkedHashSet<>(candidate.keySet());added.removeAll(old.keySet());
        Set<ResourceLocation> removed=new LinkedHashSet<>(old.keySet());removed.removeAll(candidate.keySet());
        Set<ResourceLocation> modified=new LinkedHashSet<>(),parent=new LinkedHashSet<>(),criteria=new LinkedHashSet<>(),requirements=new LinkedHashSet<>(),rewards=new LinkedHashSet<>(),display=new LinkedHashSet<>(),restored=new LinkedHashSet<>();
        for(ResourceLocation id:candidate.keySet())if(old.containsKey(id)){
            JsonObject before=old.get(id).deconstruct().serializeToJson(), after=json.get(id);
            AdvancementResourceStack stack=stacks.get(id);
            ResourceDescriptor activeDescriptor=null;
            if(context.activeReference()!=null&&stack!=null){
                ResourceLocation file=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),stack.logicalPath());
                activeDescriptor=context.activeReference().resources().get(file);
            }
            // Serialized builders contain vanilla defaults that are absent from the
            // source JSON.  Resource hashes are therefore the authoritative delta
            // signal whenever the active snapshot contains the winning file.
            boolean contentChanged=activeDescriptor!=null&&stack!=null
                    ? !activeDescriptor.fingerprint().hash().equals(stack.winningHash())
                    : !before.equals(after);
            if(contentChanged){modified.add(id);changed(before,after,"parent",id,parent);changed(before,after,"criteria",id,criteria);changed(before,after,"requirements",id,requirements);changed(before,after,"rewards",id,rewards);changed(before,after,"display",id,display);}
        }
        if(context.activeReference()!=null)for(var e:stacks.entrySet()){
            ResourceLocation file=ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),e.getValue().logicalPath());
            ResourceDescriptor before=context.activeReference().resources().get(file);
            if(before!=null&&!before.sourcePack().equals(e.getValue().winningPack())&&e.getValue().overridePacks().size()>1)restored.add(e.getKey());
        }
        return new AdvancementDelta(added,removed,modified,restored,parent,criteria,requirements,rewards,display);
    }
    private static void changed(JsonObject a,JsonObject b,String key,ResourceLocation id,Set<ResourceLocation> out){if(!Objects.equals(a.get(key),b.get(key)))out.add(id);}
    private static String snapshotFingerprint(ResourceSnapshot snapshot){if(snapshot==null)return "none";List<String> rows=new ArrayList<>();snapshot.resources().forEach((id,d)->rows.add(id+":"+d.fingerprint().hash()));Collections.sort(rows);return Integer.toHexString(rows.hashCode());}
    private static ValidationIssue issue(ValidationSeverity severity,String code,ResourceLocation id,String message){return new ValidationIssue(severity,code,ReloadCategory.ADVANCEMENTS,ID,id,null,message,null,null);}
    private static String message(Throwable value){return value.getMessage()==null?value.getClass().getSimpleName():value.getMessage();}
}
