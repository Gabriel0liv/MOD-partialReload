package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.loot.LootPreparationContext;
import com.gabriel0liv.partialreload.loot.PreparedLootData;
import com.gabriel0liv.partialreload.loot.VanillaLootDataProvider;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.gabriel0liv.partialreload.function.FunctionTransactionStatus;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartialReloadGameTests {
    private PartialReloadGameTests() {
    }

    @GameTest(template = "empty", batch = "phase1-read-only")
    public static void commandIsRegistered(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        helper.assertTrue(
                dispatcher.getRoot().getChild("partialreload") != null,
                "The /partialreload command must be registered"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase1-read-only")
    public static void statusCommandExecutes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload status"
        );
        helper.assertTrue(result == 1, "/partialreload status should succeed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase1-read-only", timeoutTicks = 1200)
    public static void scanCommandExecutes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload scan"
        );
        helper.assertTrue(result == 1, "/partialreload scan should start");
        var mod = ModList.get().getModObjectById(PartialReloadMod.MOD_ID)
                .filter(PartialReloadMod.class::isInstance)
                .map(PartialReloadMod.class::cast)
                .orElseThrow();
        helper.succeedWhen(() -> helper.assertTrue(
                mod.service().status().state() == com.gabriel0liv.partialreload.core.PartialReloadState.IDLE,
                "The read-only scan has not finished"
        ));
    }

    @GameTest(template = "empty", batch = "phase2-prepare", timeoutTicks = 200)
    public static void validFunctionsPrepareWithoutMutatingActiveServer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mod = ModList.get().getModObjectById(PartialReloadMod.MOD_ID)
                .filter(PartialReloadMod.class::isInstance)
                .map(PartialReloadMod.class::cast)
                .orElseThrow();
        mod.service().discardPrepared();

        var activeManager = server.getFunctions();
        var activeFixture = activeManager.get(
                ResourceLocation.parse("partialreload:gametest/valid")
        ).orElseThrow();
        var activeFunctionIds = StreamSupport.stream(activeManager.getFunctionNames().spliterator(), false)
                .collect(Collectors.toUnmodifiableSet());
        var activeTickIds = activeManager.getTag(VanillaFunctionsProvider.TICK_TAG).stream()
                .map(net.minecraft.commands.CommandFunction::getId)
                .collect(Collectors.toUnmodifiableSet());
        var activeLoadIds = activeManager.getTag(VanillaFunctionsProvider.LOAD_TAG).stream()
                .map(net.minecraft.commands.CommandFunction::getId)
                .collect(Collectors.toUnmodifiableSet());
        var activeRecipes = server.getRecipeManager();
        var activeLoot = server.getLootData();
        var activeAdvancements = server.getAdvancements();

        var scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("pr_prepare_probe");
        if (objective == null) {
            objective = scoreboard.addObjective(
                    "pr_prepare_probe",
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Partial Reload prepare probe"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        }
        scoreboard.getOrCreatePlayerScore("$prepared", objective).setScore(0);
        scoreboard.getOrCreatePlayerScore("$load", objective).setScore(0);
        int preparedBefore = scoreboard.getOrCreatePlayerScore("$prepared", objective).getScore();
        int loadBefore = scoreboard.getOrCreatePlayerScore("$load", objective).getScore();

        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload prepare functions"
        );
        helper.assertTrue(result == 1, "/partialreload prepare functions should start");

        Objective capturedObjective = objective;
        helper.succeedWhen(() -> {
            var artifact = mod.service().preparedFunctions();
            helper.assertTrue(artifact != null, "A prepared artifact should become available");
            helper.assertTrue(artifact.isApplicable(), "The bundled valid functions should prepare");
            helper.assertTrue(
                    artifact.functions().containsKey(ResourceLocation.parse("partialreload:gametest/valid")),
                    "The prepared generation should contain the valid fixture"
            );
            helper.assertTrue(
                    artifact.tickFunctions().contains(ResourceLocation.parse("partialreload:gametest/tick_probe")),
                    "The prepared tick set should contain the fixture"
            );
            helper.assertTrue(
                    artifact.loadFunctions().contains(ResourceLocation.parse("partialreload:gametest/load_probe")),
                    "The prepared load set should contain the fixture"
            );
            helper.assertTrue(server.getFunctions() == activeManager, "The active function manager changed");
            helper.assertTrue(
                    activeManager.get(ResourceLocation.parse("partialreload:gametest/valid"))
                            .orElseThrow() == activeFixture,
                    "The active compiled function implementation changed"
            );
            helper.assertTrue(
                    StreamSupport.stream(activeManager.getFunctionNames().spliterator(), false)
                            .collect(Collectors.toUnmodifiableSet()).equals(activeFunctionIds),
                    "The active function library changed"
            );
            helper.assertTrue(activeManager.getTag(VanillaFunctionsProvider.TICK_TAG).stream()
                            .map(net.minecraft.commands.CommandFunction::getId)
                            .collect(Collectors.toUnmodifiableSet()).equals(activeTickIds),
                    "The active tick set changed");
            helper.assertTrue(activeManager.getTag(VanillaFunctionsProvider.LOAD_TAG).stream()
                            .map(net.minecraft.commands.CommandFunction::getId)
                            .collect(Collectors.toUnmodifiableSet()).equals(activeLoadIds),
                    "The active load set changed");
            helper.assertTrue(server.getRecipeManager() == activeRecipes, "RecipeManager changed");
            helper.assertTrue(server.getLootData() == activeLoot, "LootDataManager changed");
            helper.assertTrue(server.getAdvancements() == activeAdvancements, "Advancement manager changed");
            helper.assertTrue(
                    scoreboard.getOrCreatePlayerScore("$prepared", capturedObjective).getScore() == preparedBefore,
                    "A prepared function command was executed"
            );
            helper.assertTrue(
                    scoreboard.getOrCreatePlayerScore("$load", capturedObjective).getScore() == loadBefore,
                    "A load function was executed during preparation"
            );

            // The first real commit is queued, then executed by the END tick safe point.
            if (mod.service().transaction() == null) {
                helper.assertTrue(server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "partialreload apply prepared") == 1,
                        "apply prepared should queue a function transaction");
                return;
            }
            helper.assertTrue(mod.service().transaction().status() == FunctionTransactionStatus.SUCCESS,
                    "function transaction did not complete successfully");
            helper.assertTrue(server.getFunctions().get(
                    ResourceLocation.parse("partialreload:gametest/valid")).orElseThrow() != activeFixture,
                    "commit did not publish the candidate library");
            helper.assertTrue(!com.gabriel0liv.partialreload.function.FunctionLibraryBridge
                    .loadPending(server.getFunctions()), "commit left load pending");
            helper.assertTrue(server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "partialreload rollback functions") == 1,
                    "manual rollback should queue");
            if (mod.service().transaction().status() != FunctionTransactionStatus.ROLLED_BACK) return;
            helper.assertTrue(server.getFunctions().get(
                    ResourceLocation.parse("partialreload:gametest/valid")).orElseThrow() == activeFixture,
                    "rollback did not restore the previous library");

            helper.assertTrue(mod.service().preparedArtifact() == null,
                    "successful commit must consume the prepared artifact");
        });
    }

    @GameTest(template = "empty", batch = "phase2-prepare")
    public static void invalidFunctionIsRejectedWithoutReplacingManager(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var activeManager = server.getFunctions();
        var resources = new GameTestResourceManager(Map.of(
                ResourceLocation.parse("partialreload:functions/gametest/invalid.mcfunction"),
                "definitely_not_a_registered_command"
        ));
        var provider = new VanillaFunctionsProvider(new ResourceScanner(Clock.systemUTC()));
        var context = new FunctionPreparationContext(
                resources,
                server.getCommands().getDispatcher(),
                server.getFunctionCompilationLevel(),
                Set.of(),
                Set.of(),
                Duration.ofSeconds(10),
                100,
                100,
                100,
                Clock.systemUTC(),
                UUID::randomUUID,
                System::nanoTime
        );

        final com.gabriel0liv.partialreload.function.PreparedFunctions artifact;
        try {
            artifact = provider.prepare(context);
        } catch (com.gabriel0liv.partialreload.function.FunctionPreparationException exception) {
            throw new AssertionError("Invalid source should produce an artifact with validation issues", exception);
        }
        helper.assertTrue(!artifact.isApplicable(), "An invalid function must invalidate the artifact");
        helper.assertTrue(
                artifact.validation().issues().stream()
                        .anyMatch(issue -> issue.code().equals("FUNCTION_COMMAND_ERROR")
                                && issue.sourceLocation() != null
                                && issue.sourceLocation().line() == 1),
                "The invalid command should have a structured line error"
        );
        helper.assertTrue(server.getFunctions() == activeManager, "The active function manager changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase3b-loot-prepare", timeoutTicks = 1200)
    public static void jointLootDataPreparesWithoutMutatingActiveServer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mod = ModList.get().getModObjectById(PartialReloadMod.MOD_ID)
                .filter(PartialReloadMod.class::isInstance)
                .map(PartialReloadMod.class::cast)
                .orElseThrow();
        mod.service().discardPrepared();

        var activeLoot = server.getLootData();
        var predicateId = ResourceLocation.parse("partialreload:gametest/always");
        var modifierId = ResourceLocation.parse("partialreload:gametest/set_one");
        var tableId = ResourceLocation.parse("partialreload:gametest/valid");
        var activePredicate = activeLoot.getElement(new LootDataId<>(LootDataType.PREDICATE, predicateId));
        var activeModifier = activeLoot.getElement(new LootDataId<>(LootDataType.MODIFIER, modifierId));
        var activeTable = activeLoot.getElement(new LootDataId<>(LootDataType.TABLE, tableId));
        helper.assertTrue(activePredicate != null, "Active predicate fixture was not loaded");
        helper.assertTrue(activeModifier != null, "Active modifier fixture was not loaded");
        helper.assertTrue(activeTable != null, "Active loot table fixture was not loaded");

        var activeRecipes = server.getRecipeManager();
        var activeFunctions = server.getFunctions();
        var activeAdvancements = server.getAdvancements();
        int objectiveCount = server.getScoreboard().getObjectives().size();
        var scheduledBefore =
                ((PrimaryLevelData) server.getWorldData()).getScheduledEvents().store().copy();

        BlockPos chestPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chestBefore = helper.getLevel().getBlockEntity(chestPos);
        ArmorStand marker = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
        marker.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 1))));
        marker.addTag("partialreload_non_mutation_probe");
        helper.getLevel().addFreshEntity(marker);
        Set<String> entityTagsBefore = Set.copyOf(marker.getTags());

        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(chestPos))
                .create(LootContextParamSets.CHEST);
        var activeResultBefore = activeTable.getRandomItems(params, 42L).stream()
                .map(stack -> stack.getItem().toString() + ":" + stack.getCount())
                .toList();

        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload prepare predicates"
        );
        helper.assertTrue(result == 1, "Joint loot preparation command should start");

        helper.succeedWhen(() -> {
            PreparedLootData artifact = mod.service().preparedLootData();
            if (artifact == null) {
                var state = mod.service().state();
                var status = mod.service().status();
                var transaction = mod.service().tagRecipeTransaction();
                String transactionStatus = transaction == null ? "none" : transaction.status().name();
                if (state == com.gabriel0liv.partialreload.core.PartialReloadState.PREPARING) {
                    return;
                }
                helper.fail("Prepared loot data unavailable: state=" + state
                        + ", lastError=" + status.lastError()
                        + ", preparedArtifactClass=null"
                        + ", transactionStatus=" + transactionStatus);
                return;
            }
            helper.assertTrue(artifact.isApplicable(), "Bundled loot data should validate");
            helper.assertTrue(
                    artifact.requestedCategories().equals(Set.of(ReloadCategory.PREDICATES)),
                    "Requested category was not preserved"
            );
            helper.assertTrue(
                    artifact.expandedCategories().equals(PreparedLootData.COMPLETE_SCOPE),
                    "Loot scope was not expanded"
            );
            helper.assertTrue(artifact.predicates().containsKey(predicateId), "Predicate was not prepared");
            helper.assertTrue(artifact.itemModifiers().containsKey(modifierId), "Modifier was not prepared");
            helper.assertTrue(artifact.lootTables().containsKey(tableId), "Table was not prepared");
            helper.assertTrue(
                    artifact.dependencyGraph().dependencies().stream()
                            .anyMatch(edge -> edge.target().equals(predicateId)),
                    "Predicate reference was not graphed"
            );
            helper.assertTrue(
                    artifact.dependencyGraph().dependencies().stream()
                            .anyMatch(edge -> edge.target().equals(modifierId)),
                    "Modifier reference was not graphed"
            );

            helper.assertTrue(server.getLootData() == activeLoot, "Active LootDataManager changed");
            helper.assertTrue(
                    activeLoot.getElement(new LootDataId<>(LootDataType.PREDICATE, predicateId))
                            == activePredicate,
                    "Active predicate instance changed"
            );
            helper.assertTrue(
                    activeLoot.getElement(new LootDataId<>(LootDataType.MODIFIER, modifierId))
                            == activeModifier,
                    "Active modifier instance changed"
            );
            helper.assertTrue(
                    activeLoot.getElement(new LootDataId<>(LootDataType.TABLE, tableId))
                            == activeTable,
                    "Active loot table instance changed"
            );
            helper.assertTrue(server.getRecipeManager() == activeRecipes, "RecipeManager changed");
            helper.assertTrue(server.getFunctions() == activeFunctions, "ServerFunctionManager changed");
            helper.assertTrue(server.getAdvancements() == activeAdvancements, "Advancement manager changed");
            helper.assertTrue(
                    server.getScoreboard().getObjectives().size() == objectiveCount,
                    "Scoreboards changed"
            );
            helper.assertTrue(
                    ((PrimaryLevelData) server.getWorldData())
                            .getScheduledEvents().store().equals(scheduledBefore),
                    "Scheduled functions changed"
            );
            helper.assertTrue(
                    helper.getLevel().getBlockEntity(chestPos) == chestBefore,
                    "Container block entity changed"
            );
            helper.assertTrue(marker.getTags().equals(entityTagsBefore), "Entity tags changed");
            var activeResultAfter = activeTable.getRandomItems(params, 42L).stream()
                    .map(stack -> stack.getItem().toString() + ":" + stack.getCount())
                    .toList();
            helper.assertTrue(
                    activeResultAfter.equals(activeResultBefore),
                    "Active loot behavior changed"
            );

            helper.assertTrue(
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), "partialreload prepared"
                    ) == 1,
                    "Prepared command should inspect loot artifact"
            );
            helper.assertTrue(
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), "partialreload apply anything"
                    ) == 0,
                    "Apply stub must remain blocked"
            );
            helper.assertTrue(
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), "partialreload discard"
                    ) == 1,
                    "Discard should remove the candidate"
            );
            helper.assertTrue(mod.service().preparedArtifact() == null, "Discard left an artifact");
            helper.assertTrue(server.getLootData() == activeLoot, "Discard changed active manager");
            marker.discard();
        });
    }

    @GameTest(template = "empty", batch = "phase3b-loot-prepare")
    public static void invalidPredicateInvalidatesJointCandidate(GameTestHelper helper) {
        PreparedLootData artifact = prepareLootFixture(helper, Map.of(
                ResourceLocation.parse("partialreload:predicates/bad.json"),
                "{\"condition\":\"partialreload:not_registered\"}"
        ));
        helper.assertTrue(!artifact.isApplicable(), "Invalid predicate must invalidate candidate");
        helper.assertTrue(
                artifact.validation().issues().stream()
                        .anyMatch(issue -> issue.code().equals("LOOT_UNKNOWN_CONDITION_TYPE")
                                || issue.code().equals("LOOT_DESERIALIZATION_ERROR")),
                "Invalid predicate should be structured"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase3b-loot-prepare")
    public static void invalidModifierInvalidatesJointCandidate(GameTestHelper helper) {
        PreparedLootData artifact = prepareLootFixture(helper, Map.of(
                ResourceLocation.parse("partialreload:item_modifiers/bad.json"),
                "{\"function\":\"partialreload:not_registered\"}"
        ));
        helper.assertTrue(!artifact.isApplicable(), "Invalid modifier must invalidate candidate");
        helper.assertTrue(
                artifact.validation().issues().stream()
                        .anyMatch(issue -> issue.code().equals("LOOT_UNKNOWN_FUNCTION_TYPE")
                                || issue.code().equals("LOOT_DESERIALIZATION_ERROR")),
                "Invalid modifier should be structured"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase3b-loot-prepare")
    public static void invalidTableAndMissingReferenceInvalidateJointCandidate(GameTestHelper helper) {
        PreparedLootData invalid = prepareLootFixture(helper, Map.of(
                ResourceLocation.parse("partialreload:loot_tables/bad.json"),
                "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"partialreload:not_registered\"}]}]}"
        ));
        helper.assertTrue(!invalid.isApplicable(), "Invalid table must invalidate candidate");

        PreparedLootData missing = prepareLootFixture(helper, Map.of(
                ResourceLocation.parse("partialreload:loot_tables/missing.json"),
                "{\"pools\":[{\"rolls\":1,\"entries\":["
                        + "{\"type\":\"minecraft:loot_table\",\"name\":\"partialreload:absent\"}]}]}"
        ));
        helper.assertTrue(!missing.isApplicable(), "Missing table reference must invalidate candidate");
        helper.assertTrue(
                missing.validation().issues().stream()
                        .anyMatch(issue -> issue.code().equals("LOOT_TABLE_REFERENCE_MISSING")),
                "Missing table should have a structured issue"
        );
        helper.succeed();
    }

    private static PreparedLootData prepareLootFixture(
            GameTestHelper helper,
            Map<ResourceLocation, String> resources
    ) {
        var server = helper.getLevel().getServer();
        var activeManager = server.getLootData();
        var provider = new VanillaLootDataProvider(new ResourceScanner(Clock.systemUTC()));
        var context = new LootPreparationContext(
                new GameTestResourceManager(resources),
                Set.of(ReloadCategory.LOOT),
                null,
                Duration.ofSeconds(10),
                100,
                100,
                100,
                1_000_000,
                10_000,
                Clock.systemUTC(),
                UUID::randomUUID,
                System::nanoTime
        );
        try {
            PreparedLootData artifact = provider.prepare(context);
            helper.assertTrue(server.getLootData() == activeManager, "Active manager changed");
            return artifact;
        } catch (com.gabriel0liv.partialreload.loot.LootPreparationException exception) {
            throw new AssertionError("Content errors should produce an invalid artifact", exception);
        }
    }
}
