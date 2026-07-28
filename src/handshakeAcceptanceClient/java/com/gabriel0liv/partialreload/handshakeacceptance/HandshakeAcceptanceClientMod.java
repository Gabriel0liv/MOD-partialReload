package com.gabriel0liv.partialreload.handshakeacceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Disposable client-only launcher used only by the real handshake acceptance harness. */
@Mod(HandshakeAcceptanceClientMod.MOD_ID)
public final class HandshakeAcceptanceClientMod {
    public static final String MOD_ID = "partialreload_handshake_acceptance";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_STABLE_TICKS = 5;
    private final boolean expectedWithMod = Boolean.parseBoolean(
            System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_WITH_MOD", "true"));
    private AcceptanceClientState state = AcceptanceClientState.BOOTING;
    private int stableTicks;
    private long diagnosticTicks;
    private boolean initialReadyEmitted;
    private boolean reconnectReadyEmitted;

    private enum AcceptanceClientState {
        BOOTING, READY, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, RECONNECTING, FAILED
    }

    public HandshakeAcceptanceClientMod() {
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
        MinecraftForge.EVENT_BUS.addListener(this::loggingIn);
        MinecraftForge.EVENT_BUS.addListener(this::loggingOut);
    }

    private void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (state == AcceptanceClientState.CONNECTING || state == AcceptanceClientState.RECONNECTING) {
            state = AcceptanceClientState.CONNECTED;
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN state=CONNECTED");
        }
    }

    private void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (state == AcceptanceClientState.CONNECTED || state == AcceptanceClientState.DISCONNECTING) {
            state = AcceptanceClientState.DISCONNECTED;
            stableTicks = 0;
            reconnectReadyEmitted = false;
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT state=DISCONNECTED");
        }
    }

    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == AcceptanceClientState.FAILED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        diagnosticTicks++;
        dismissAccessibilityOnboarding(minecraft);
        if (diagnosticTicks % 100 == 0 && state == AcceptanceClientState.BOOTING) {
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_BOOT_STATE screenClass={} overlayPresent={} connectionPresent={} levelPresent={} partialReloadLoaded={} helperLoaded={} stableTicks={}",
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName(),
                    minecraft.getOverlay() != null, minecraft.getConnection() != null, minecraft.level != null,
                    ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID), stableTicks);
        }
        Path control = controlDirectory();
        if (state == AcceptanceClientState.BOOTING || state == AcceptanceClientState.READY) {
            if (!validateModSet()) {
                state = AcceptanceClientState.FAILED;
                LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_MOD_SET_INVALID expectedWithMod={} partialreload={} helper={}",
                        expectedWithMod, ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID));
                return;
            }
            if (isInitialConnectionReady(minecraft)) {
                stableTicks++;
            } else {
                stableTicks = 0;
            }
            if (stableTicks >= REQUIRED_STABLE_TICKS && !initialReadyEmitted) {
                initialReadyEmitted = true;
                state = AcceptanceClientState.READY;
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_READY screenClass={} overlayPresent={} connectionPresent={} levelPresent={} partialReloadLoaded={} helperLoaded={} stableTicks={}",
                        minecraft.screen.getClass().getSimpleName(), minecraft.getOverlay() != null,
                        minecraft.getConnection() != null, minecraft.level != null,
                        ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID), stableTicks);
            }
        }
        if ((state == AcceptanceClientState.READY || state == AcceptanceClientState.DISCONNECTED)
                && Files.exists(control.resolve("connect.request"))) {
            connect(minecraft, control.resolve("connect.request"), false);
            return;
        }
        if (state == AcceptanceClientState.DISCONNECTED && minecraft.getConnection() == null
                && minecraft.level == null && minecraft.screen != null) {
            if (isReconnectReady(minecraft)) {
                stableTicks++;
            } else {
                stableTicks = 0;
            }
            if (stableTicks >= REQUIRED_STABLE_TICKS && !reconnectReadyEmitted) {
                reconnectReadyEmitted = true;
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY screenClass={} stableTicks={}",
                        minecraft.screen.getClass().getSimpleName(), stableTicks);
            }
            if (reconnectReadyEmitted && Files.exists(control.resolve("reconnect.request"))) {
                connect(minecraft, control.resolve("reconnect.request"), true);
                return;
            }
        }
        if (minecraft.getConnection() != null && Files.exists(control.resolve("disconnect.request"))) {
            try {
                Files.deleteIfExists(control.resolve("disconnect.request"));
                state = AcceptanceClientState.DISCONNECTING;
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED");
                minecraft.getConnection().getConnection().disconnect(Component.literal("acceptance disconnect"));
            } catch (IOException exception) {
                fail("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FAILED", exception);
            }
        }
        if (Files.exists(control.resolve("exit.request"))) {
            try {
                Files.deleteIfExists(control.resolve("exit.request"));
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_EXIT_REQUESTED");
                minecraft.stop();
            } catch (IOException exception) {
                fail("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FAILED", exception);
            }
        }
    }

    private void connect(Minecraft minecraft, Path request, boolean reconnect) {
        if (!isInitialConnectionReady(minecraft) && !isReconnectReady(minecraft)) {
            return;
        }
        try {
            Files.deleteIfExists(request);
            state = reconnect ? AcceptanceClientState.RECONNECTING : AcceptanceClientState.CONNECTING;
            LOGGER.info(reconnect ? "HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED" :
                    "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED");
            startConnection(minecraft);
        } catch (IOException | RuntimeException exception) {
            fail("HANDSHAKE_ACCEPTANCE_CLIENT_FAILED", exception);
        }
    }

    private boolean validateModSet() {
        return ModList.get().isLoaded(MOD_ID) && ModList.get().isLoaded("partialreload") == expectedWithMod;
    }

    private static boolean isInitialConnectionReady(Minecraft minecraft) {
        return minecraft.getOverlay() == null && minecraft.getConnection() == null
                && minecraft.level == null && minecraft.screen instanceof TitleScreen;
    }

    private static void dismissAccessibilityOnboarding(Minecraft minecraft) {
        if (minecraft.screen instanceof AccessibilityOnboardingScreen
                || (minecraft.screen != null
                && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
            minecraft.options.onboardAccessibility = false;
            minecraft.setScreen(new TitleScreen(false));
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_ACCESSIBILITY_DISMISSED");
        }
    }

    private static boolean isReconnectReady(Minecraft minecraft) {
        return minecraft.getOverlay() == null && minecraft.getConnection() == null
                && minecraft.level == null && minecraft.screen != null;
    }

    private Path controlDirectory() {
        return Path.of(System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_CONTROL_DIR", "."));
    }

    private void startConnection(Minecraft minecraft) {
        String host = System.getenv("PARTIALRELOAD_ACCEPTANCE_HOST");
        String port = System.getenv("PARTIALRELOAD_ACCEPTANCE_PORT");
        ServerAddress address = ServerAddress.parseString(host + ":" + port);
        ServerData data = new ServerData("Partial Reload handshake acceptance", host + ":" + port, false);
        ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data, false);
    }

    private void fail(String message, Exception exception) {
        state = AcceptanceClientState.FAILED;
        LOGGER.error(message, exception);
    }
}
