package com.gabriel0liv.partialreload.handshakeacceptance;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Disposable client-only launcher used only by the real handshake acceptance harness. */
@Mod(HandshakeAcceptanceClientMod.MOD_ID)
public final class HandshakeAcceptanceClientMod {
    public static final String MOD_ID = "partialreload_handshake_acceptance";
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean connectionStarted;
    private boolean reconnectStarted;

    public HandshakeAcceptanceClientMod() {
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Path control = Path.of(System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_CONTROL_DIR", ""));
        if (Files.exists(control.resolve("disconnect.request"))) {
            try {
                Files.deleteIfExists(control.resolve("disconnect.request"));
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.getConnection() != null) {
                    LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED");
                    minecraft.getConnection().getConnection().disconnect(Component.literal("acceptance disconnect"));
                }
            } catch (IOException exception) {
                LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FAILED", exception);
            }
            return;
        }
        if (Files.exists(control.resolve("reconnect.request")) && Minecraft.getInstance().getConnection() == null
                && Minecraft.getInstance().screen != null && !reconnectStarted) {
            try {
                Files.deleteIfExists(control.resolve("reconnect.request"));
                reconnectStarted = true;
                startConnection(Minecraft.getInstance(), "HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED");
            } catch (IOException exception) {
                LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FAILED", exception);
            }
            return;
        }
        if (connectionStarted) {
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
            connectionStarted = true;
            startConnection(minecraft, "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECTING");
        } catch (RuntimeException exception) {
            LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_FAILED", exception);
            connectionStarted = true;
        }
    }

    private static void startConnection(Minecraft minecraft, String marker) {
        String host = System.getenv("PARTIALRELOAD_ACCEPTANCE_HOST");
        String port = System.getenv("PARTIALRELOAD_ACCEPTANCE_PORT");
        ServerAddress address = ServerAddress.parseString(host + ":" + port);
        ServerData data = new ServerData("Partial Reload handshake acceptance", host + ":" + port, false);
        LOGGER.info("{} host={} port={}", marker, host, port);
        ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data, false);
    }

}
