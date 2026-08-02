package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.core.ConnectedPlayerProbe;
import com.gabriel0liv.partialreload.core.DeferredPlayerSession;
import com.gabriel0liv.partialreload.core.PartialReloadState;
import com.gabriel0liv.partialreload.joint.MappedRegistryTagBridge;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitTransaction;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultPoint;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultInjection;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionEvent;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionStatus;
import com.gabriel0liv.partialreload.joint.TagRecipeConnectedPlayerPolicy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TagRecipeTransactionGameTests {
    private TagRecipeTransactionGameTests() {
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private static void runTransactionalScenario(GameTestHelper helper, String name,
                                                  ThrowingConsumer<TagRecipeGameTestFixture> scenario) {
        TagRecipeGameTestFixture fixture = null;
        Throwable failure = null;
        try {
            fixture = TagRecipeGameTestFixture.create(helper);
            scenario.accept(fixture);
        } catch (Throwable throwable) {
            failure = throwable;
        }
        try {
            if (fixture != null) {
                fixture.close();
                fixture.assertCleanup();
            }
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure != null) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            helper.fail(trace.toString());
            return;
        }
        PartialReloadMod.LOGGER.info("PHASE4E_GAMETEST_PASSED:{}", name);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void successfulCommitPublishesGenerationB(GameTestHelper helper) {
        runTransactionalScenario(helper, "successfulCommitPublishesGenerationB", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-commit-b");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS,
                    "commit did not succeed: " + transaction.status());
            helper.assertTrue(transaction.verificationPassed(), "commit verification failed");
            fixture.assertGenerationB();
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void manualRollbackRestoresGenerationA(GameTestHelper helper) {
        runTransactionalScenario(helper, "manualRollbackRestoresGenerationA", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            TagRecipeCommitTransaction commit = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-commit-b-before-rollback");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(commit.status() == TagRecipeTransactionStatus.SUCCESS, "commit failed");
            fixture.assertGenerationB();
            TagRecipeCommitTransaction rollback = fixture.service()
                    .requestTagRecipeRollback("gametest-manual-rollback");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(rollback != commit, "rollback reused commit transaction");
            helper.assertTrue(!rollback.transactionId().equals(commit.transactionId()), "rollback UUID reused");
            helper.assertTrue(rollback.status() == TagRecipeTransactionStatus.ROLLED_BACK,
                    "rollback did not complete: " + rollback.status());
            helper.assertTrue(rollback.verificationPassed(), "rollback verification failed");
            helper.assertTrue(rollback.ingredientRollbackInvalidations() == 1, "rollback invalidations");
            helper.assertTrue(rollback.rollbackTagEvents() == 1, "rollback tag events");
            fixture.assertGenerationA();
            assertEventsInOrder(helper, rollback,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_RECIPES_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENT_INVALIDATION_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENTS_INVALIDATED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCH_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCHED,
                    TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_PASSED);
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void playerPresentAtRequestIsRejected(GameTestHelper helper) {
        runTransactionalScenario(helper, "playerPresentAtRequestIsRejected", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.fixedPlayerCount(1);
            TagRecipeCommitTransaction before = fixture.service().tagRecipeTransaction();
            try {
                fixture.service().requestTagRecipeCommit(fixture.server(), "gametest-player-present");
                throw new AssertionError("request unexpectedly accepted");
            } catch (IllegalStateException expected) {
                helper.assertTrue(expected.getMessage().contains("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED"),
                        "wrong rejection: " + expected.getMessage());
            }
            helper.assertTrue(fixture.service().tagRecipeTransaction() == before, "transaction created");
            helper.assertTrue(fixture.service().state() == PartialReloadState.READY, "state changed");
            helper.assertTrue(fixture.service().preparedTagsAndRecipes() == fixture.generationBArtifact(),
                    "prepared artifact changed");
            fixture.assertGenerationA();
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void playerRaceAtSafePointFailsSafe(GameTestHelper helper) {
        runTransactionalScenario(helper, "playerRaceAtSafePointFailsSafe", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.fixedPlayerCount(0);
            fixture.holdSafePoint();
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-player-race");
            fixture.fixedPlayerCount(1);
            fixture.releaseSafePoint();
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.FAILED_SAFE,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(transaction.failure().contains("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED"),
                    "wrong failure: " + transaction.failure());
            helper.assertTrue(fixture.service().state() == PartialReloadState.FAILED_SAFE, "state not failed safe");
            fixture.assertNoMutationCounters(transaction);
            fixture.assertGenerationA();
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.PREFLIGHT_PASSED,
                    TagRecipeTransactionEventType.SAFE_POINT_REACHED,
                    TagRecipeTransactionEventType.FAILURE,
                    TagRecipeTransactionEventType.STATUS_CHANGED);
            assertEventsAbsent(helper, transaction,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACED_EXACT,
                    TagRecipeTransactionEventType.RECIPE_PUBLICATION_STARTED,
                    TagRecipeTransactionEventType.RECIPES_PUBLISHED,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED);
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void normalCommandRemainsBlockedWithPlayer(GameTestHelper helper) {
        runTransactionalScenario(helper, "normalCommandRemainsBlockedWithPlayer", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.fixedPlayerCount(1);
            int result = fixture.server().getCommands().performPrefixedCommand(
                    fixture.server().createCommandSourceStack(), "partialreload apply prepared");
            helper.assertTrue(result == 0, "normal command accepted a connected player");
            helper.assertTrue(fixture.service().state() == PartialReloadState.READY, "normal command changed state");
            fixture.assertGenerationA();
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void deferredCommitClosesMenusMarksStaleAndPublishesImmediately(GameTestHelper helper) {
        runTransactionalScenario(helper, "deferredCommitClosesMenusMarksStaleAndPublishesImmediately", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID playerId = UUID.randomUUID();
            AtomicBoolean inventoryMenu = new AtomicBoolean(false);
            List<net.minecraft.network.chat.Component> messages = new ArrayList<>();
            fixture.deferredSessions(List.of(new DeferredPlayerSession(playerId, "deferred_player",
                    () -> inventoryMenu.set(true), inventoryMenu::get, messages::add)));
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "deferred commit failed");
            helper.assertTrue(inventoryMenu.get(), "menu was not closed before commit");
            helper.assertTrue(transaction.deferredPlayerSnapshot().keySet().equals(java.util.Set.of(playerId)),
                    "safe-point player snapshot mismatch");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().isStale(playerId), "player not stale");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().activeGeneration() == 1,
                    "generation did not increment exactly once");
            helper.assertTrue(messages.size() == 1, "relog warning not sent exactly once");
            fixture.assertGenerationB();
            fixture.assertLifecycleGenerationB();
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void menuCloseFailurePreventsFirstMutation(GameTestHelper helper) {
        runTransactionalScenario(helper, "menuCloseFailurePreventsFirstMutation", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID playerId = UUID.randomUUID();
            fixture.deferredSessions(List.of(new DeferredPlayerSession(playerId, "stuck_menu",
                    () -> {}, () -> false, ignored -> {})));
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-menu-failure", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.FAILED_SAFE,
                    "menu failure was not fail-safe");
            helper.assertTrue(transaction.failure().contains("TAG_RECIPE_DEFERRED_MENU_CLOSE_FAILED"),
                    "wrong menu failure: " + transaction.failure());
            fixture.assertNoMutationCounters(transaction);
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().staleCount() == 0,
                    "menu failure marked stale");
            fixture.assertGenerationA();
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void loginLogoutAndPostCommitJoinClearStale(GameTestHelper helper) {
        runTransactionalScenario(helper, "loginLogoutAndPostCommitJoinClearStale", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            fixture.deferredSessions(List.of(session(first, "first"), session(second, "second")));
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-lifecycle", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "deferred commit failed");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().staleCount() == 2, "two players not stale");
            UUID joinedAfter = UUID.randomUUID();
            fixture.service().onPlayerLogin(joinedAfter);
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(joinedAfter),
                    "post-commit join became stale");
            fixture.service().onPlayerLogout(first);
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(first), "logout did not clear stale");
            fixture.service().onPlayerLogin(second);
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(second), "login did not clear stale");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void deferredSafePointCapturesPlayerWhoJoinedAfterInitialPreflight(GameTestHelper helper) {
        runTransactionalScenario(helper, "deferredSafePointCapturesPlayerWhoJoinedAfterInitialPreflight", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.deferredSessions(List.of());
            fixture.holdSafePoint();
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-join-race", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            UUID joined = UUID.randomUUID();
            fixture.deferredSessions(List.of(session(joined, "joined_at_safe_point")));
            fixture.releaseSafePoint();
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "join-race commit failed");
            helper.assertTrue(transaction.deferredPlayerSnapshot().keySet().equals(java.util.Set.of(joined)),
                    "safe point did not capture newly joined player");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().isStale(joined),
                    "newly joined player was not marked stale");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void deferredSafePointOmitsPlayerWhoLeftAfterInitialPreflight(GameTestHelper helper) {
        runTransactionalScenario(helper, "deferredSafePointOmitsPlayerWhoLeftAfterInitialPreflight", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID left = UUID.randomUUID();
            fixture.deferredSessions(List.of(session(left, "left_before_safe_point")));
            fixture.holdSafePoint();
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-leave-race", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.deferredSessions(List.of());
            fixture.releaseSafePoint();
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "leave-race commit failed");
            helper.assertTrue(transaction.deferredPlayerSnapshot().isEmpty(),
                    "safe point retained a player who had disconnected");
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(left),
                    "disconnected player was marked stale");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void automaticRollbackDoesNotMarkStale(GameTestHelper helper) {
        runTransactionalScenario(helper, "automaticRollbackDoesNotMarkStale", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID playerId = UUID.randomUUID();
            fixture.deferredSessions(List.of(session(playerId, "rollback_player")));
            fixture.armFault(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION);
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-rollback", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.ROLLED_BACK, "rollback failed");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().activeGeneration() == 0,
                    "failed commit incremented generation");
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(playerId),
                    "rolled-back commit marked stale");
            fixture.assertGenerationA();
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void degradedDeferredCommitIsNeverReportedAsSuccess(GameTestHelper helper) {
        runTransactionalScenario(helper, "degradedDeferredCommitIsNeverReportedAsSuccess", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            UUID playerId = UUID.randomUUID();
            fixture.deferredSessions(List.of(session(playerId, "degraded_player")));
            fixture.armFaultSequence(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION, TagRecipeFaultPoint.DURING_ROLLBACK);
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-degraded", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.DEGRADED, "not degraded");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().activeGeneration() == 0,
                    "degraded commit incremented generation");
            helper.assertTrue(!fixture.service().deferredClientRefreshTracker().isStale(playerId),
                    "degraded commit marked stale");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void deferredWithoutPlayersSucceedsWithZeroStale(GameTestHelper helper) {
        runTransactionalScenario(helper, "deferredWithoutPlayersSucceedsWithZeroStale", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.deferredSessions(List.of());
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-empty", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "empty deferred failed");
            helper.assertTrue(transaction.deferredClientRefreshGeneration() == 1, "generation mismatch");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().staleCount() == 0, "stale not zero");
            fixture.assertGenerationB();
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void safePointIsIdempotentAfterDeferredSuccess(GameTestHelper helper) {
        runTransactionalScenario(helper, "safePointIsIdempotentAfterDeferredSuccess", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.deferredSessions(List.of());
            TagRecipeCommitTransaction transaction = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-idempotent", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            fixture.service().processTagRecipeSafePoint(fixture.server());
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.SUCCESS, "commit failed");
            helper.assertTrue(fixture.service().deferredClientRefreshTracker().activeGeneration() == 1,
                    "safe point incremented generation twice");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void concurrentDeferredCommitIsRejected(GameTestHelper helper) {
        runTransactionalScenario(helper, "concurrentDeferredCommitIsRejected", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.deferredSessions(List.of());
            fixture.holdSafePoint();
            TagRecipeCommitTransaction first = fixture.service().requestTagRecipeCommit(fixture.server(),
                    "gametest-deferred-first", TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
            IllegalStateException failure;
            try {
                fixture.service().requestTagRecipeCommit(fixture.server(), "gametest-deferred-concurrent",
                        TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN);
                throw new AssertionError("concurrent deferred commit was accepted");
            } catch (IllegalStateException expected) {
                failure = expected;
            }
            helper.assertTrue(failure.getMessage().contains("TAG_RECIPE_COMMIT_TRANSACTION_RUNNING"),
                    "wrong concurrent rejection: " + failure.getMessage());
            fixture.releaseSafePoint();
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(first.status() == TagRecipeTransactionStatus.SUCCESS,
                    "original deferred transaction did not complete");
        });
    }

    @GameTest(template = "empty", batch = "phase4f-r-deferred-client-refresh", timeoutTicks = 1200)
    public static void manualRollbackWithPlayersRemainsBlocked(GameTestHelper helper) {
        runTransactionalScenario(helper, "manualRollbackWithPlayersRemainsBlocked", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            TagRecipeCommitTransaction commit = fixture.service().requestTagRecipeCommit(fixture.server(), "commit-before-rollback");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(commit.status() == TagRecipeTransactionStatus.SUCCESS, "setup commit failed");
            fixture.fixedPlayerCount(1);
            IllegalStateException failure;
            try {
                fixture.service().requestTagRecipeRollback(fixture.server(), "blocked-rollback");
                throw new AssertionError("manual rollback accepted connected players");
            } catch (IllegalStateException expected) {
                failure = expected;
            }
            helper.assertTrue(failure.getMessage().contains("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED"),
                    "wrong rollback failure");
        });
    }

    private static DeferredPlayerSession session(UUID playerId, String name) {
        AtomicBoolean inventoryMenu = new AtomicBoolean(false);
        return new DeferredPlayerSession(playerId, name, () -> inventoryMenu.set(true), inventoryMenu::get,
                ignored -> {});
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void beforeFirstTagBindFailsSafe(GameTestHelper helper) {
        runTransactionalScenario(helper, "beforeFirstTagBindFailsSafe", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.armFault(TagRecipeFaultPoint.BEFORE_FIRST_TAG_BIND);
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-before-first-bind");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.FAILED_SAFE,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(transaction.failure().contains("FAULT_INJECTED:BEFORE_FIRST_TAG_BIND"),
                    "wrong failure: " + transaction.failure());
            helper.assertTrue(transaction.registriesToMutate().size() == 1, "unexpected registry scope");
            fixture.assertNoMutationCounters(transaction);
            fixture.assertGenerationA();
            helper.assertTrue(TagRecipeFaultInjection.pending().isEmpty(), "fault was not consumed");
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.SAFE_POINT_REACHED,
                    TagRecipeTransactionEventType.REGISTRY_SCOPE_DERIVED,
                    TagRecipeTransactionEventType.CANDIDATE_BINDINGS_BUILT,
                    TagRecipeTransactionEventType.CANDIDATE_RECIPES_BUILT,
                    TagRecipeTransactionEventType.PREVIOUS_GENERATION_CAPTURED,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.TAG_BIND_STARTED,
                    TagRecipeTransactionEventType.FAILURE);
            assertEventsAbsent(helper, transaction,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACED_EXACT,
                    TagRecipeTransactionEventType.RECIPES_PUBLISHED,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED);
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void afterRecipePublicationRollsBack(GameTestHelper helper) {
        runTransactionalScenario(helper, "afterRecipePublicationRollsBack", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.armFault(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION);
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-after-recipe-publication");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.ROLLED_BACK,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(transaction.failure().contains("FAULT_INJECTED:AFTER_RECIPE_PUBLICATION"),
                    "wrong failure: " + transaction.failure());
            helper.assertTrue(transaction.verificationPassed(), "rollback verification failed");
            helper.assertTrue(!transaction.restartRequired(), "restart unexpectedly required");
            helper.assertTrue(transaction.tagMutationOccurred(), "tag mutation was not recorded");
            helper.assertTrue(transaction.recipePublicationOccurred(), "recipe publication was not recorded");
            helper.assertTrue(transaction.ingredientCommitInvalidations() == 0, "commit invalidation count");
            helper.assertTrue(transaction.commitTagEvents() == 0, "commit tag event count");
            helper.assertTrue(transaction.ingredientRollbackInvalidations() == 1, "rollback invalidation count");
            helper.assertTrue(transaction.rollbackTagEvents() == 1, "rollback tag event count");
            fixture.assertGenerationA();
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACED_EXACT,
                    TagRecipeTransactionEventType.RECIPE_PUBLICATION_STARTED,
                    TagRecipeTransactionEventType.RECIPES_PUBLISHED,
                    TagRecipeTransactionEventType.FAILURE,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_RECIPES_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENT_INVALIDATION_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENTS_INVALIDATED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCH_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCHED,
                    TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_PASSED);
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void beforeRollbackVerificationDegrades(GameTestHelper helper) {
        runTransactionalScenario(helper, "beforeRollbackVerificationDegrades", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.armFaultSequence(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION,
                    TagRecipeFaultPoint.BEFORE_ROLLBACK_VERIFICATION);
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-before-rollback-verification");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.DEGRADED,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(fixture.service().state() == PartialReloadState.DEGRADED, "service not degraded");
            helper.assertTrue(transaction.restartRequired(), "restart was not required");
            helper.assertTrue(transaction.failure().contains("TAG_RECIPE_ROLLBACK_FAILED"),
                    "wrong failure: " + transaction.failure());
            helper.assertTrue(transaction.tagMutationOccurred() && transaction.recipePublicationOccurred(),
                    "rollback did not observe prior mutations");
            helper.assertTrue(transaction.ingredientCommitInvalidations() == 0 && transaction.commitTagEvents() == 0,
                    "commit counters changed unexpectedly");
            helper.assertTrue(transaction.ingredientRollbackInvalidations() == 1 && transaction.rollbackTagEvents() == 1,
                    "rollback counters incorrect");
            helper.assertTrue(!transaction.verificationPassed(), "verification unexpectedly passed");
            fixture.assertGenerationA();
            assertLockout(helper, fixture, transaction);
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.FAILURE,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_RECIPES_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENT_INVALIDATION_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_INGREDIENTS_INVALIDATED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCH_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCHED,
                    TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_STARTED,
                    TagRecipeTransactionEventType.DEGRADED);
            assertEventsAbsent(helper, transaction, TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_PASSED);
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void duringRollbackDegrades(GameTestHelper helper) {
        runTransactionalScenario(helper, "duringRollbackDegrades", fixture -> {
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            fixture.armFaultSequence(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION,
                    TagRecipeFaultPoint.DURING_ROLLBACK);
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-during-rollback");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.DEGRADED,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(fixture.service().state() == PartialReloadState.DEGRADED, "service not degraded");
            helper.assertTrue(transaction.restartRequired(), "restart was not required");
            helper.assertTrue(transaction.failure().contains("TAG_RECIPE_ROLLBACK_FAILED")
                    && transaction.failure().contains("FAULT_INJECTED:DURING_ROLLBACK"),
                    "wrong failure: " + transaction.failure());
            helper.assertTrue(transaction.tagMutationOccurred() && transaction.recipePublicationOccurred(),
                    "prior mutation not recorded");
            helper.assertTrue(transaction.ingredientCommitInvalidations() == 0
                    && transaction.commitTagEvents() == 0
                    && transaction.ingredientRollbackInvalidations() == 0
                    && transaction.rollbackTagEvents() == 0, "unexpected counters");
            helper.assertTrue(!transaction.verificationPassed(), "verification unexpectedly passed");
            fixture.assertGenerationB();
            assertLockout(helper, fixture, transaction);
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.RECIPES_PUBLISHED,
                    TagRecipeTransactionEventType.FAILURE,
                    TagRecipeTransactionEventType.DEGRADED);
            assertEventsAbsent(helper, transaction,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED,
                    TagRecipeTransactionEventType.ROLLBACK_RECIPES_RESTORED,
                    TagRecipeTransactionEventType.ROLLBACK_TAG_RESTORED);
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void tagLifecyclePreservesMissingEmptyAndRemoved(GameTestHelper helper) {
        runTransactionalScenario(helper, "tagLifecyclePreservesMissingEmptyAndRemoved", fixture -> {
            fixture.installGenerationA();
            fixture.prepareLifecycleGenerationB();
            fixture.assertLifecycleGenerationA();
            var oldRemovedNamed = fixture.captureRemovedTagNamedSet();
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.STONE, id("removed_tag"), true);
            TagRecipeCommitTransaction commit = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-tag-lifecycle");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(commit.status() == TagRecipeTransactionStatus.SUCCESS, "lifecycle commit failed");
            helper.assertTrue(commit.verificationPassed(), "lifecycle commit verification failed");
            fixture.assertLifecycleGenerationB();
            helper.assertTrue(oldRemovedNamed.size() == 0, "old named set retained members");
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.STONE, id("removed_tag"), false);
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.DIRT, id("new_tag"), true);
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.DIRT, id("empty_tag"), true);
            TagRecipeCommitTransaction rollback = fixture.service()
                    .requestTagRecipeRollback("gametest-tag-lifecycle-rollback");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(rollback.status() == TagRecipeTransactionStatus.ROLLED_BACK, "lifecycle rollback failed");
            helper.assertTrue(rollback.verificationPassed(), "lifecycle rollback verification failed");
            fixture.assertLifecycleGenerationA();
            helper.assertTrue(oldRemovedNamed.size() == 0, "old named set was repopulated");
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.STONE, id("removed_tag"), true);
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.DIRT, id("new_tag"), false);
            fixture.assertItemHolderHasTag(net.minecraft.world.item.Items.DIRT, id("empty_tag"), false);
            fixture.assertRegistryIdentitiesPreserved();
        });
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void unsupportedRegistryFailsSafe(GameTestHelper helper) {
        runTransactionalScenario(helper, "unsupportedRegistryFailsSafe", fixture -> {
            fixture.installGenerationA();
            fixture.prepareUnsupportedGenerationB();
            TagRecipeCommitTransaction transaction = fixture.service()
                    .requestTagRecipeCommit(fixture.server(), "gametest-unsupported-registry");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(transaction.status() == TagRecipeTransactionStatus.FAILED_SAFE,
                    "wrong terminal: " + transaction.status());
            helper.assertTrue(fixture.service().state() == PartialReloadState.FAILED_SAFE, "service not failed safe");
            helper.assertTrue(transaction.failure().contains("TAG_REGISTRY_COMMIT_UNSUPPORTED"),
                    "wrong failure: " + transaction.failure());
            fixture.assertNoMutationCounters(transaction);
            helper.assertTrue(!transaction.tagMutationOccurred(), "tag mutation occurred");
            fixture.assertGenerationA();
            assertEventsInOrder(helper, transaction,
                    TagRecipeTransactionEventType.SAFE_POINT_REACHED,
                    TagRecipeTransactionEventType.FAILURE);
            assertEventsAbsent(helper, transaction,
                    TagRecipeTransactionEventType.CANDIDATE_BINDINGS_BUILT,
                    TagRecipeTransactionEventType.TAG_REGISTRY_REPLACEMENT_STARTED,
                    TagRecipeTransactionEventType.RECIPES_PUBLISHED,
                    TagRecipeTransactionEventType.ROLLBACK_STARTED);
        });
    }

    private static void assertLockout(GameTestHelper helper, TagRecipeGameTestFixture fixture,
                                      TagRecipeCommitTransaction original) {
        try {
            fixture.service().requestTagRecipeCommit(fixture.server(), "gametest-lockout-apply");
            throw new AssertionError("degraded apply was accepted");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("TAG_RECIPE_TRANSACTION_DEGRADED"),
                    "wrong apply lockout: " + expected.getMessage());
        }
        try {
            fixture.service().requestTagRecipeRollback("gametest-lockout-rollback");
            throw new AssertionError("degraded rollback was accepted");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("TAG_RECIPE_TRANSACTION_DEGRADED"),
                    "wrong rollback lockout: " + expected.getMessage());
        }
        helper.assertTrue(fixture.service().tagRecipeTransaction() == original, "degraded transaction replaced");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("partialreload", "gametest_tx/" + path);
    }

    private static void assertEventsInOrder(GameTestHelper helper, TagRecipeCommitTransaction transaction,
                                            TagRecipeTransactionEventType... expected) {
        List<TagRecipeTransactionEvent> events = transaction.events();
        int previous = -1;
        for (TagRecipeTransactionEventType type : expected) {
            int found = -1;
            for (int index = previous + 1; index < events.size(); index++) {
                if (events.get(index).type() == type) {
                    found = index;
                    break;
                }
            }
            helper.assertTrue(found >= 0, "missing event " + type + "; observed=" + events.stream()
                    .map(event -> event.type().name()).toList());
            if (found >= 0) {
                helper.assertTrue(events.get(found).transactionId().equals(transaction.transactionId()),
                        "event UUID mismatch for " + type);
                previous = found;
            }
        }
    }

    private static void assertEventsAbsent(GameTestHelper helper, TagRecipeCommitTransaction transaction,
                                           TagRecipeTransactionEventType... forbidden) {
        List<TagRecipeTransactionEventType> observed = transaction.events().stream()
                .map(TagRecipeTransactionEvent::type).toList();
        for (TagRecipeTransactionEventType type : forbidden) {
            helper.assertTrue(!observed.contains(type), "forbidden event " + type + "; observed=" + observed);
        }
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction")
    public static void forgeWrapperIsRecognized(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", "item"));
        var compatibility = MappedRegistryTagBridge.inspect(server.registryAccess(), key);
        helper.assertTrue(compatibility.compatible(), "item registry bridge must be compatible");
        helper.assertTrue(compatibility.kind() == MappedRegistryTagBridge.Kind.FORGE_NAMESPACED_WRAPPER,
                "item registry must use Forge NamespacedWrapper");
        PartialReloadMod.LOGGER.info("PHASE4E_GAMETEST_PASSED:forgeWrapperIsRecognized");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction")
    public static void registryIdentityIsStable(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", "item"));
        int before = System.identityHashCode(server.registryAccess().registryOrThrow(key));
        int after = System.identityHashCode(server.registryAccess().registryOrThrow(key));
        helper.assertTrue(before == after, "registry identity changed");
        PartialReloadMod.LOGGER.info("PHASE4E_GAMETEST_PASSED:registryIdentityIsStable");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction")
    public static void defaultPlayerProbeUsesRealServerList(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(ConnectedPlayerProbe.DEFAULT.playerCount(server) == server.getPlayerList().getPlayerCount(),
                "default probe did not read the real player list");
        PartialReloadMod.LOGGER.info("PHASE4E_GAMETEST_PASSED:defaultPlayerProbeUsesRealServerList");
        helper.succeed();
    }
}
