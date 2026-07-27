package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.core.ConnectedPlayerProbe;
import com.gabriel0liv.partialreload.core.PartialReloadState;
import com.gabriel0liv.partialreload.joint.MappedRegistryTagBridge;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitTransaction;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultPoint;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultInjection;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionEvent;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionStatus;
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
