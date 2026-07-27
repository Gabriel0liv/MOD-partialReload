package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ChangeDetector;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipes;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipesFactory;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.tags.PreparedTagsResolutionView;
import com.gabriel0liv.partialreload.recipe.VanillaRecipesProvider;
import com.gabriel0liv.partialreload.gametest.GameTestResourceManager;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.tags.VanillaTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TagRecipeGameTestPreparation {
    record PreparedFixtureGeneration(ResourceSnapshot snapshot, ChangeSet changeSet,
                                     PreparedTagsAndRecipes artifact) {
    }

    static PreparedFixtureGeneration prepare(MinecraftServer server,
                                             Map<ResourceLocation, String> resources,
                                             ResourceSnapshot baseline) {
        Map<ResourceLocation, ResourceDescriptor> descriptors = new LinkedHashMap<>();
        resources.forEach((location, contents) -> descriptors.put(location,
                new ResourceDescriptor(location, logicalId(location), category(location), "gametest",
                        ResourceFingerprint.sha256(contents.getBytes(StandardCharsets.UTF_8)))));

        ResourceSnapshot snapshot = new ResourceSnapshot(Instant.now(), descriptors);
        GameTestResourceManager resourceManager = new GameTestResourceManager(resources);
        PreparedTags preparedTags = new VanillaTagsProvider().prepare(resourceManager, server.registryAccess(),
                snapshot, baseline, 100, 100, 1_000, 100_000, 10_000_000_000L, UUID.randomUUID());
        if (!preparedTags.isApplicable()) {
            throw new IllegalStateException("gametest tags invalid: " + preparedTags.validation().issues());
        }

        Set<ResourceLocation> changedTagIds = new LinkedHashSet<>(preparedTags.delta().tagsAdded());
        changedTagIds.addAll(preparedTags.delta().tagsModified());
        changedTagIds.addAll(preparedTags.delta().tagsRemoved());
        PreparedRecipes preparedRecipes = new VanillaRecipesProvider().prepareWithCandidateTags(resourceManager,
                snapshot, baseline, new PreparedTagsResolutionView(preparedTags), changedTagIds,
                100, 100_000, 10_000_000_000L, UUID.randomUUID());
        if (!preparedRecipes.isApplicable()) {
            throw new IllegalStateException("gametest recipes invalid: " + preparedRecipes.validation().issues());
        }

        PreparedTagsAndRecipes artifact = PreparedTagsAndRecipesFactory.combine(
                UUID.randomUUID(), snapshot, preparedTags, preparedRecipes);
        if (!artifact.isApplicable()) {
            throw new IllegalStateException("gametest joint artifact invalid");
        }
        ResourceSnapshot previous = baseline == null
                ? new ResourceSnapshot(Instant.now(), Map.of())
                : baseline;
        return new PreparedFixtureGeneration(snapshot, ChangeDetector.diff(previous, snapshot), artifact);
    }

    private static ReloadCategory category(ResourceLocation location) {
        return location.getPath().startsWith("tags/") ? ReloadCategory.TAGS : ReloadCategory.RECIPES;
    }

    private static ResourceLocation logicalId(ResourceLocation location) {
        String path = location.getPath();
        String remainder = path.substring(path.startsWith("tags/") ? 5 : 8);
        int slash = remainder.indexOf('/');
        if (slash >= 0) {
            remainder = remainder.substring(slash + 1);
        }
        if (remainder.endsWith(".json")) {
            remainder = remainder.substring(0, remainder.length() - 5);
        }
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), remainder);
    }
}
