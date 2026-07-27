package com.gabriel0liv.partialreload.gametest;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ChangeDetector;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipes;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipesFactory;
import com.gabriel0liv.partialreload.recipe.*;
import com.gabriel0liv.partialreload.resource.*;
import com.gabriel0liv.partialreload.tags.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.time.Instant; import java.util.*;
final class TagRecipeGameTestPreparation {
    record PreparedFixtureGeneration(ResourceSnapshot snapshot, ChangeSet changeSet, PreparedTagsAndRecipes artifact) {}
    static PreparedFixtureGeneration prepare(MinecraftServer server, Map<ResourceLocation,String> resources, ResourceSnapshot baseline) {
        Map<ResourceLocation,ResourceDescriptor> descriptors=new LinkedHashMap<>(); resources.forEach((l,j)->descriptors.put(l,new ResourceDescriptor(l,logical(l),l.getPath().startsWith("tags/")?ReloadCategory.TAGS:ReloadCategory.RECIPES,"gametest",ResourceFingerprint.sha256(j.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        ResourceSnapshot snapshot=new ResourceSnapshot(Instant.now(),descriptors); GameTestResourceManager manager=new GameTestResourceManager(resources);
        PreparedTags tags=new VanillaTagsProvider().prepare(manager,server.registryAccess(),snapshot,baseline,100,100,1000,100000,10_000_000_000L,UUID.randomUUID()); if(!tags.isApplicable())throw new IllegalStateException("gametest tags invalid: "+tags.validation().issues());
        Set<ResourceLocation> changed=new LinkedHashSet<>(tags.delta().tagsAdded());changed.addAll(tags.delta().tagsModified());changed.addAll(tags.delta().tagsRemoved());
        PreparedRecipes recipes=new VanillaRecipesProvider().prepareWithCandidateTags(manager,snapshot,baseline,new PreparedTagsResolutionView(tags),changed,100,100000,10_000_000_000L,UUID.randomUUID());if(!recipes.isApplicable())throw new IllegalStateException("gametest recipes invalid: "+recipes.validation().issues());
        PreparedTagsAndRecipes artifact=PreparedTagsAndRecipesFactory.combine(UUID.randomUUID(),snapshot,tags,recipes);if(!artifact.isApplicable())throw new IllegalStateException("gametest joint invalid");
        ResourceSnapshot before=baseline==null?new ResourceSnapshot(Instant.now(),Map.of()):baseline; return new PreparedFixtureGeneration(snapshot,ChangeDetector.diff(before,snapshot),artifact);
    }
    private static ResourceLocation logical(ResourceLocation l){String p=l.getPath();String rest=p.substring(p.startsWith("tags/")?5:8);int slash=rest.indexOf('/');if(slash>=0)rest=rest.substring(slash+1);if(rest.endsWith(".json"))rest=rest.substring(0,rest.length()-5);return ResourceLocation.fromNamespaceAndPath(l.getNamespace(),rest);}
}
