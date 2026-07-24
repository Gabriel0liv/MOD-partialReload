package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import net.minecraft.resources.ResourceLocation;
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

    @GameTest(template = "empty", batch = "phase1-read-only", timeoutTicks = 400)
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

            int preparedResult = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "partialreload prepared"
            );
            helper.assertTrue(preparedResult == 1, "/partialreload prepared should succeed");
            int discardResult = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "partialreload discard"
            );
            helper.assertTrue(discardResult == 1, "/partialreload discard should succeed");
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
}
