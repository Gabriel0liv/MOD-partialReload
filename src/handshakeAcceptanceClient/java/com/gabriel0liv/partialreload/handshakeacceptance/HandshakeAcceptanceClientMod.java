package com.gabriel0liv.partialreload.handshakeacceptance;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/** Disposable client-only launcher used only by the real handshake acceptance harness. */
@Mod(HandshakeAcceptanceClientMod.MOD_ID)
public final class HandshakeAcceptanceClientMod {
    public static final String MOD_ID = "partialreload_handshake_acceptance";
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean connectionStarted;

    public HandshakeAcceptanceClientMod() {
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || connectionStarted) {
            return;
        }
        String host = System.getenv("PARTIALRELOAD_ACCEPTANCE_HOST");
        String port = System.getenv("PARTIALRELOAD_ACCEPTANCE_PORT");
        if (host == null || port == null || host.isBlank() || port.isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return;
        }
        try {
            ServerAddress address = ServerAddress.parseString(host + ":" + port);
            ServerData data = new ServerData("Partial Reload handshake acceptance",
                    host + ":" + port, false);
            connectionStarted = true;
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECTING host={} port={}", host, port);
            ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data, false);
        } catch (RuntimeException exception) {
            LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_FAILED", exception);
            connectionStarted = true;
        }
    }

}
