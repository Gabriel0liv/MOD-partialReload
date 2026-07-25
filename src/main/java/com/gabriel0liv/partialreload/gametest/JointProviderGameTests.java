package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.recipe.VanillaRecipesProvider;
import com.gabriel0liv.partialreload.resource.*;
import com.gabriel0liv.partialreload.tags.PreparedTagsResolutionView;
import com.gabriel0liv.partialreload.tags.VanillaTagsProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JointProviderGameTests {
    private JointProviderGameTests() { }

    @GameTest(template = "empty", batch = "phase4d-joint-prepare")
    public static void realProvidersUseCandidateTagView(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation tagFile = ResourceLocation.parse("partialreload_test:tags/items/joint.json");
        ResourceLocation recipeFile = ResourceLocation.parse("partialreload_test:recipes/acceptance.json");
        String tagJson = "{\"replace\":true,\"values\":[\"minecraft:dirt\"]}";
        String recipeJson = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"partialreload_test:joint\"}],\"result\":{\"item\":\"minecraft:torch\"}}";
        var resources = new GameTestResourceManager(Map.of(tagFile, tagJson, recipeFile, recipeJson));
        var descriptors = Map.of(
                tagFile, new ResourceDescriptor(tagFile, ResourceLocation.parse("partialreload_test:tags/items/joint"), ReloadCategory.TAGS, "fixture", ResourceFingerprint.sha256(tagJson.getBytes())),
                recipeFile, new ResourceDescriptor(recipeFile, ResourceLocation.parse("partialreload_test:recipes/acceptance"), ReloadCategory.RECIPES, "fixture", ResourceFingerprint.sha256(recipeJson.getBytes())));
        var snapshot = new ResourceSnapshot(Instant.now(), descriptors);
        var tags = new VanillaTagsProvider().prepare(resources, server.registryAccess(), snapshot, null, 10, 10, 20, 10000, 10_000_000_000L, UUID.randomUUID());
        helper.assertTrue(tags.isApplicable(), "tag candidate should be applicable");
        var recipes = new VanillaRecipesProvider().prepareWithCandidateTags(resources, snapshot, snapshot,
                new PreparedTagsResolutionView(tags), Set.of(ResourceLocation.parse("partialreload_test:joint")),
                10, 10000, 10_000_000_000L, UUID.randomUUID());
        helper.assertTrue(recipes.isApplicable(), "recipe candidate should be applicable");
        helper.assertTrue(recipes.recipesImpactedByTagChanges().contains(ResourceLocation.parse("partialreload_test:acceptance")), "recipe impact was not propagated");
        helper.assertTrue(recipes.revalidatedDueToTagChange().contains(ResourceLocation.parse("partialreload_test:acceptance")), "recipe was not revalidated");
        helper.succeed();
    }
}
