package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.core.ConnectedPlayerProbe;
import com.gabriel0liv.partialreload.joint.MappedRegistryTagBridge;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Focused server-side invariants for the 4E-S safety gate. */
@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TagRecipeTransactionGameTests {
    private TagRecipeTransactionGameTests() {}

    @GameTest(template = "empty", batch = "phase4e-tag-recipe-transaction", timeoutTicks = 1200)
    public static void successfulCommitPublishesGenerationB(GameTestHelper helper) {
        TagRecipeGameTestFixture fixture = null; Throwable failure = null;
        try {
            fixture = TagRecipeGameTestFixture.create(helper);
            fixture.installGenerationA();
            fixture.prepareGenerationB();
            var tx = fixture.service().requestTagRecipeCommit(fixture.server(), "gametest-commit-b");
            fixture.service().processTagRecipeSafePoint(fixture.server());
            helper.assertTrue(tx.status() == com.gabriel0liv.partialreload.joint.TagRecipeTransactionStatus.SUCCESS, "commit did not succeed: " + tx.status());
            helper.assertTrue(tx.verificationPassed(), "commit verification failed");
            fixture.assertGenerationB();
        } catch (Throwable t) { failure = t; }
        try { if (fixture != null) fixture.close(); if (fixture != null) fixture.assertCleanup(); }
        catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
        if (failure != null) { helper.fail(failure.toString()); return; }
        PartialReloadMod.LOGGER.info("PHASE4E_GAMETEST_PASSED:successfulCommitPublishesGenerationB");
        helper.succeed();
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
        var before = System.identityHashCode(server.registryAccess().registryOrThrow(key));
        var after = System.identityHashCode(server.registryAccess().registryOrThrow(key));
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
