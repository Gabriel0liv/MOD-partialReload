package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartialReloadGameTests {
    private PartialReloadGameTests() {
    }

    @GameTest(template = "empty")
    public static void commandIsRegistered(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        helper.assertTrue(
                dispatcher.getRoot().getChild("partialreload") != null,
                "The /partialreload command must be registered"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void statusCommandExecutes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload status"
        );
        helper.assertTrue(result == 1, "/partialreload status should succeed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanCommandExecutes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "partialreload scan"
        );
        helper.assertTrue(result == 1, "/partialreload scan should start");
        helper.succeed();
    }
}
