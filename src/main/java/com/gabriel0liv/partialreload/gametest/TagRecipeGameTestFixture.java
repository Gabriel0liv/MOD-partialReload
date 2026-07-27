package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.PartialReloadState;
import com.gabriel0liv.partialreload.core.TagRecipeGameTestState;
import com.gabriel0liv.partialreload.joint.ActiveTagRecipeGeneration;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitTransaction;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultInjection;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultPoint;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionStatus;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipes;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class TagRecipeGameTestFixture implements AutoCloseable {
    private static final ResourceLocation ITEM_TAG_ID = id("item_joint");
    private static final ResourceLocation RECIPE_ID = id("acceptance");

    private final GameTestHelper helper;
    private final MinecraftServer server;
    private final PartialReloadService service;
    private final TagRecipeGameTestState originalState;
    private final ActiveTagRecipeGeneration originalPhysical;
    private final int registryAccessIdentity;
    private final int itemRegistryIdentity;
    private final int recipeManagerIdentity;
    private final boolean originalItemTagPresent;
    private final boolean originalRecipePresent;
    private TagRecipeGameTestPreparation.PreparedFixtureGeneration generationA;
    private TagRecipeGameTestPreparation.PreparedFixtureGeneration generationB;
    private boolean closed;

    static TagRecipeGameTestFixture create(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PartialReloadMod mod = ModList.get().getModObjectById(PartialReloadMod.MOD_ID)
                .filter(PartialReloadMod.class::isInstance)
                .map(PartialReloadMod.class::cast)
                .orElseThrow();
        return new TagRecipeGameTestFixture(helper, server, mod.service());
    }

    private TagRecipeGameTestFixture(GameTestHelper helper, MinecraftServer server, PartialReloadService service) {
        this.helper = helper;
        this.server = server;
        this.service = service;
        if (!TagRecipeFaultInjection.pending().isEmpty()) {
            throw new IllegalStateException("fault queue not empty");
        }
        this.originalState = service.captureTagRecipeGameTestState();
        this.registryAccessIdentity = System.identityHashCode(server.registryAccess());
        this.itemRegistryIdentity = System.identityHashCode(server.registryAccess().registryOrThrow(Registries.ITEM));
        this.recipeManagerIdentity = System.identityHashCode(server.getRecipeManager());
        this.originalItemTagPresent = server.registryAccess().registryOrThrow(Registries.ITEM)
                .getTag(TagKey.create(Registries.ITEM, ITEM_TAG_ID)).isPresent();
        this.originalRecipePresent = server.getRecipeManager().byKey(RECIPE_ID).isPresent();
        this.generationA = TagRecipeGameTestPreparation.prepare(server, resources(false), null);
        this.originalPhysical = service.captureTagRecipeGenerationForGameTest(server, generationA.artifact());
    }

    PartialReloadService service() {
        return service;
    }

    MinecraftServer server() {
        return server;
    }

    PreparedTagsAndRecipes generationBArtifact() {
        if (generationB == null) {
            throw new IllegalStateException("generation B is not prepared");
        }
        return generationB.artifact();
    }

    void installGenerationA() {
        if (generationA == null) {
            throw new IllegalStateException("generation A is not prepared");
        }
        ResourceSnapshot empty = new ResourceSnapshot(Instant.now(), Map.of());
        service.installTagRecipeGameTestReadyState(empty, generationA.snapshot(), generationA.changeSet(),
                generationA.artifact(), (candidateServer, expected) -> expected == generationA.snapshot());
        TagRecipeCommitTransaction setup = service.requestTagRecipeCommit(server, "gametest-setup-a");
        service.processTagRecipeSafePoint(server);
        if (setup.status() != TagRecipeTransactionStatus.SUCCESS || !setup.verificationPassed()) {
            throw new AssertionError("setup A failed: " + setup.status());
        }
    }

    void prepareGenerationB() {
        if (generationA == null) {
            throw new IllegalStateException("install or prepare generation A first");
        }
        generationB = TagRecipeGameTestPreparation.prepare(server, resources(true), generationA.snapshot());
        service.installTagRecipeGameTestReadyState(generationA.snapshot(), generationB.snapshot(), generationB.changeSet(),
                generationB.artifact(), (candidateServer, expected) -> expected == generationB.snapshot());
    }

    void prepareLifecycleGenerationB() {
        prepareGenerationB();
    }

    void prepareUnsupportedGenerationB() {
        if (generationA == null) {
            throw new IllegalStateException("generation A is not prepared");
        }
        Map<ResourceLocation, String> resources = resources(true);
        resources.put(location("tags/worldgen/biome/gametest_tx/unsupported.json"),
                "{\"replace\":true,\"values\":[\"minecraft:plains\"]}");
        generationB = TagRecipeGameTestPreparation.prepare(server, resources, generationA.snapshot());
        service.installTagRecipeGameTestReadyState(generationA.snapshot(), generationB.snapshot(),
                generationB.changeSet(), generationB.artifact(),
                (candidateServer, expected) -> expected == generationB.snapshot());
    }

    void fixedPlayerCount(int count) {
        service.fixedConnectedPlayerProbe(count);
    }

    void resetPlayerProbe() {
        service.resetConnectedPlayerProbe();
    }

    void holdSafePoint() {
        service.holdTagRecipeSafePoint();
    }

    void releaseSafePoint() {
        service.releaseTagRecipeSafePoint();
    }

    void armFault(TagRecipeFaultPoint point) {
        TagRecipeFaultInjection.failAt(point);
    }

    void armFaultSequence(TagRecipeFaultPoint... points) {
        for (TagRecipeFaultPoint point : points) {
            armFault(point);
        }
    }

    void assertGenerationA() {
        assertItemTag("item_joint", "minecraft:stone");
        assertRecipe(1);
    }

    void assertPhysicalGenerationA() {
        assertGenerationA();
    }

    void assertGenerationB() {
        assertItemTag("item_joint", "minecraft:dirt");
        assertRecipe(2);
    }

    void assertLifecycleGenerationA() {
        assertGenerationA();
        assertItemTagMissing(id("new_tag"));
        assertItemTagEmpty(id("empty_tag"));
        assertItemTagMembers(id("removed_tag"), ResourceLocation.parse("minecraft:stone"));
    }

    void assertLifecycleGenerationB() {
        assertGenerationB();
        assertItemTagMembers(id("new_tag"), ResourceLocation.parse("minecraft:dirt"));
        assertItemTagMembers(id("empty_tag"), ResourceLocation.parse("minecraft:dirt"));
        assertItemTagMissing(id("removed_tag"));
    }

    HolderSet.Named<Item> captureRemovedTagNamedSet() {
        return itemRegistry().getTag(TagKey.create(Registries.ITEM, id("removed_tag")))
                .orElseThrow(() -> new AssertionError("removed tag missing before commit"));
    }

    void assertItemTagMissing(ResourceLocation tagId) {
        helper.assertTrue(itemRegistry().getTag(TagKey.create(Registries.ITEM, tagId)).isEmpty(),
                "tag should be missing: " + tagId);
    }

    void assertItemTagEmpty(ResourceLocation tagId) {
        var named = itemRegistry().getTag(TagKey.create(Registries.ITEM, tagId))
                .orElseThrow(() -> new AssertionError("tag should be present: " + tagId));
        helper.assertTrue(named.size() == 0, "tag should be empty: " + tagId);
    }

    void assertItemTagMembers(ResourceLocation tagId, ResourceLocation... expectedMembers) {
        var named = itemRegistry().getTag(TagKey.create(Registries.ITEM, tagId))
                .orElseThrow(() -> new AssertionError("tag missing: " + tagId));
        var expected = java.util.Arrays.stream(expectedMembers).sorted().toList();
        var observed = named.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value())).sorted().toList();
        helper.assertTrue(observed.equals(expected), "unexpected members for " + tagId + ": " + observed);
    }

    void assertItemHolderHasTag(Item item, ResourceLocation tagId, boolean expected) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(item));
        Holder<Item> holder = itemRegistry().getHolder(itemKey)
                .orElseThrow(() -> new AssertionError("item holder missing: " + itemKey.location()));
        helper.assertTrue(holder.is(TagKey.create(Registries.ITEM, tagId)) == expected,
                "unexpected membership for " + itemKey.location() + " in " + tagId);
    }

    void assertNoMutationCounters(TagRecipeCommitTransaction transaction) {
        helper.assertTrue(transaction.mutatedTagRegistries().isEmpty(), "unexpected mutated registries");
        helper.assertTrue(!transaction.recipePublicationOccurred(), "recipe publication occurred");
        helper.assertTrue(transaction.ingredientCommitInvalidations() == 0, "commit invalidation occurred");
        helper.assertTrue(transaction.ingredientRollbackInvalidations() == 0, "rollback invalidation occurred");
        helper.assertTrue(transaction.commitTagEvents() == 0, "commit tag event occurred");
        helper.assertTrue(transaction.rollbackTagEvents() == 0, "rollback tag event occurred");
    }

    void assertRegistryIdentitiesPreserved() {
        helper.assertTrue(System.identityHashCode(server.registryAccess()) == registryAccessIdentity,
                "RegistryAccess identity changed");
        helper.assertTrue(System.identityHashCode(server.registryAccess().registryOrThrow(Registries.ITEM)) == itemRegistryIdentity,
                "item registry identity changed");
        helper.assertTrue(System.identityHashCode(server.getRecipeManager()) == recipeManagerIdentity,
                "RecipeManager identity changed");
    }

    void assertItemTag(String name, String expectedMember) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, id(name));
        var named = server.registryAccess().registryOrThrow(Registries.ITEM).getTag(key)
                .orElseThrow(() -> new AssertionError("tag missing: " + key.location()));
        var members = named.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value())).toList();
        helper.assertTrue(members.size() == 1 && Objects.equals(members.get(0), ResourceLocation.parse(expectedMember)),
                "unexpected members for " + key.location() + ": " + members);
    }

    private Registry<Item> itemRegistry() {
        return server.registryAccess().registryOrThrow(Registries.ITEM);
    }

    void assertRecipe(int expectedCount) {
        Recipe<?> recipe = server.getRecipeManager().byKey(RECIPE_ID)
                .orElseThrow(() -> new AssertionError("fixture recipe missing"));
        var result = recipe.getResultItem(server.registryAccess());
        helper.assertTrue(result.is(Items.TORCH) && result.getCount() == expectedCount,
                "unexpected recipe result/count");
        helper.assertTrue(recipe.getSerializer() == RecipeSerializer.SHAPELESS_RECIPE,
                "unexpected recipe serializer");
        helper.assertTrue(recipe.getType() == RecipeType.CRAFTING, "unexpected recipe type");
        helper.assertTrue(countRecipeOccurrences(server.getRecipeManager(), recipe) == 1,
                "recipe index occurrence mismatch");
    }

    private static long countRecipeOccurrences(RecipeManager manager, Recipe<?> recipe) {
        return countRecipeOccurrencesCaptured(manager, recipe.getType(), recipe.getId());
    }

    @SuppressWarnings("unchecked")
    private static <C extends net.minecraft.world.Container, T extends Recipe<C>> long countRecipeOccurrencesCaptured(
            RecipeManager manager, RecipeType<?> type, ResourceLocation id) {
        return countRecipeOccurrences(manager, (RecipeType<T>) type, id);
    }

    private static <C extends net.minecraft.world.Container, T extends Recipe<C>> long countRecipeOccurrences(
            RecipeManager manager, RecipeType<T> type, ResourceLocation id) {
        return manager.getAllRecipesFor(type).stream().filter(recipe -> recipe.getId().equals(id)).count();
    }

    void assertCleanup() {
        assertRegistryIdentitiesPreserved();
        helper.assertTrue(service.tagRecipeTransaction() == originalState.tagRecipeTransaction(),
                "transaction was not restored");
        helper.assertTrue(service.preparedTagsAndRecipes() == originalState.preparedArtifact(),
                "prepared artifact was not restored");
        helper.assertTrue(service.state() == originalState.state(), "state was not restored");
        helper.assertTrue(TagRecipeFaultInjection.pending().isEmpty(), "fault queue not empty");
        helper.assertTrue(!service.tagRecipeSafePointHeldForGameTest(), "safe point remained held");
        if (!originalItemTagPresent) {
            helper.assertTrue(server.registryAccess().registryOrThrow(Registries.ITEM).getTag(
                    TagKey.create(Registries.ITEM, ITEM_TAG_ID)).isEmpty(), "fixture tag remained");
        }
        if (!originalRecipePresent) {
            helper.assertTrue(server.getRecipeManager().byKey(RECIPE_ID).isEmpty(), "fixture recipe remained");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            releaseSafePoint();
            resetPlayerProbe();
            TagRecipeFaultInjection.clear();
            service.restoreTagRecipeGenerationForGameTest(server, originalPhysical);
            service.restoreTagRecipeGameTestState(originalState);
        } finally {
            closed = true;
        }
    }

    private Map<ResourceLocation, String> resources(boolean candidate) {
        String member = candidate ? "minecraft:dirt" : "minecraft:stone";
        int count = candidate ? 2 : 1;
        Map<ResourceLocation, String> resources = new LinkedHashMap<>();
        resources.put(location("tags/items/gametest_tx/item_joint.json"),
                "{\"replace\":true,\"values\":[\"" + member + "\"]}");
        if (candidate) {
            resources.put(location("tags/items/gametest_tx/new_tag.json"),
                    "{\"replace\":true,\"values\":[\"minecraft:dirt\"]}");
            resources.put(location("tags/items/gametest_tx/empty_tag.json"),
                    "{\"replace\":true,\"values\":[\"minecraft:dirt\"]}");
        } else {
            resources.put(location("tags/items/gametest_tx/empty_tag.json"),
                    "{\"replace\":true,\"values\":[]}");
            resources.put(location("tags/items/gametest_tx/removed_tag.json"),
                    "{\"replace\":true,\"values\":[\"minecraft:stone\"]}");
        }
        resources.put(location("recipes/gametest_tx/acceptance.json"),
                "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"partialreload:gametest_tx/item_joint\"}],\"result\":{\"item\":\"minecraft:torch\",\"count\":" + count + "}}");
        return resources;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("partialreload", "gametest_tx/" + path);
    }

    private static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath("partialreload", path);
    }
}
