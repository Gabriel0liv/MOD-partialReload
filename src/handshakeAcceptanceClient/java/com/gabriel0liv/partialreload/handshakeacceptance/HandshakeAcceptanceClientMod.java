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
    private boolean exitRequested;
    private boolean stopAfterLogout;
    private PendingAction pendingAction;
    private long ticksSinceRequest;
    private String previousScreen;

    private enum PendingAction { INITIAL_CONNECT, RECONNECT }

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
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN run={} attempt={} state=CONNECTED", runId(), attemptId());
        }
    }

    private void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (state == AcceptanceClientState.CONNECTED || state == AcceptanceClientState.DISCONNECTING) {
            state = AcceptanceClientState.DISCONNECTED;
            stableTicks = 0;
            reconnectReadyEmitted = false;
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT run={} attempt={} state=DISCONNECTED", runId(), attemptId());
        }
    }

    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == AcceptanceClientState.FAILED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        diagnosticTicks++;
        if (exitRequested && stopAfterLogout && minecraft.getConnection() == null && minecraft.level == null) {
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_STOPPING_AFTER_LOGOUT run={} attempt={} state={} thread={}",
                    runId(), attemptId(), state, Thread.currentThread().getName());
            stopAfterLogout = false;
            minecraft.stop();
            return;
        }
        dismissAccessibilityOnboarding(minecraft);
        String screen = minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName();
        if (!screen.equals(previousScreen)) {
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_SCREEN_CHANGED run={} attempt={} state={} previousScreen={} currentScreen={} overlayPresent={} connectionPresent={} levelPresent={} thread={}",
                    runId(), attemptId(), state, previousScreen == null ? "null" : previousScreen, screen,
                    minecraft.getOverlay() != null, minecraft.getConnection() != null, minecraft.level != null,
                    Thread.currentThread().getName());
            if ("DisconnectedScreen".equals(screen) && minecraft.screen != null) {
                String narration = minecraft.screen.getNarrationMessage().getString()
                        .replace('\n', ' ').replace('\r', ' ');
                if (narration.length() > 512) {
                    narration = narration.substring(0, 512);
                }
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN run={} attempt={} state={} screen={} narration={} ticksSinceRequest={}",
                        runId(), attemptId(), state, screen, narration, ticksSinceRequest);
            }
            previousScreen = screen;
        }
        if (state == AcceptanceClientState.CONNECTING || state == AcceptanceClientState.RECONNECTING) {
            ticksSinceRequest++;
            if (ticksSinceRequest % 100 == 0) {
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_HEARTBEAT run={} attempt={} state={} screen={} overlayPresent={} connectionPresent={} levelPresent={} ticksSinceRequest={} thread={}",
                        runId(), attemptId(), state, screen, minecraft.getOverlay() != null,
                        minecraft.getConnection() != null, minecraft.level != null, ticksSinceRequest,
                        Thread.currentThread().getName());
            }
            if (pendingAction != null && ticksSinceRequest >= 1) {
                PendingAction action = pendingAction;
                pendingAction = null;
                invokeConnection(minecraft, action == PendingAction.RECONNECT);
                return;
            }
        }
        if (diagnosticTicks % 100 == 0 && state == AcceptanceClientState.BOOTING) {
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_BOOT_STATE run={} attempt={} screenClass={} overlayPresent={} connectionPresent={} levelPresent={} partialReloadLoaded={} helperLoaded={} stableTicks={}",
                    runId(), attemptId(),
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName(),
                    minecraft.getOverlay() != null, minecraft.getConnection() != null, minecraft.level != null,
                    ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID), stableTicks);
        }
        Path control = controlDirectory();
        if (state == AcceptanceClientState.BOOTING || state == AcceptanceClientState.READY) {
            if (!validateModSet()) {
                state = AcceptanceClientState.FAILED;
            LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_MOD_SET_INVALID run={} attempt={} expectedWithMod={} partialreload={} helper={}",
                    runId(), attemptId(), expectedWithMod, ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID));
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
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_READY run={} attempt={} screenClass={} overlayPresent={} connectionPresent={} levelPresent={} partialReloadLoaded={} helperLoaded={} stableTicks={}",
                        runId(), attemptId(),
                        minecraft.screen.getClass().getSimpleName(), minecraft.getOverlay() != null,
                        minecraft.getConnection() != null, minecraft.level != null,
                        ModList.get().isLoaded("partialreload"), ModList.get().isLoaded(MOD_ID), stableTicks);
                if ("LAUNCH_ARGS".equalsIgnoreCase(initialConnectMode())) {
                    LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_LAUNCH_ARGS_ARMED run={} attempt={} state={} thread={}",
                            runId(), attemptId(), state, Thread.currentThread().getName());
                }
            }
        }
        if ((state == AcceptanceClientState.READY || state == AcceptanceClientState.DISCONNECTED)
                && !"LAUNCH_ARGS".equalsIgnoreCase(initialConnectMode())
                && Files.exists(control.resolve("connect.request"))) {
            consumeRequest(minecraft, control.resolve("connect.request"), false);
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
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY run={} attempt={} screenClass={} stableTicks={}",
                        runId(), attemptId(), minecraft.screen.getClass().getSimpleName(), stableTicks);
            }
            if (reconnectReadyEmitted && Files.exists(control.resolve("reconnect.request"))) {
                consumeRequest(minecraft, control.resolve("reconnect.request"), true);
                return;
            }
        }
        if (minecraft.getConnection() != null && Files.exists(control.resolve("disconnect.request"))) {
            try {
                Files.deleteIfExists(control.resolve("disconnect.request"));
                state = AcceptanceClientState.DISCONNECTING;
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED run={} attempt={} state={}", runId(), attemptId(), state);
                minecraft.getConnection().getConnection().disconnect(Component.literal("acceptance disconnect"));
            } catch (IOException exception) {
                LOGGER.warn("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FILE_DELETE_RETRY run={} attempt={} requestFile=disconnect.request",
                        runId(), attemptId(), exception);
            }
        }
        if (Files.exists(control.resolve("exit.request"))) {
            try {
                Files.deleteIfExists(control.resolve("exit.request"));
                LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_EXIT_REQUESTED run={} attempt={} state={}", runId(), attemptId(), state);
                exitRequested = true;
                stopAfterLogout = true;
                if (minecraft.getConnection() != null) {
                    state = AcceptanceClientState.DISCONNECTING;
                    minecraft.getConnection().getConnection().disconnect(Component.literal("acceptance exit"));
                } else {
                    state = AcceptanceClientState.DISCONNECTED;
                }
            } catch (IOException exception) {
                LOGGER.warn("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FILE_DELETE_RETRY run={} attempt={} requestFile=exit.request",
                        runId(), attemptId(), exception);
            }
        }
    }

    private void consumeRequest(Minecraft minecraft, Path request, boolean reconnect) {
        if (!isInitialConnectionReady(minecraft) && !isReconnectReady(minecraft)) {
            return;
        }
        try {
            Files.deleteIfExists(request);
            state = reconnect ? AcceptanceClientState.RECONNECTING : AcceptanceClientState.CONNECTING;
            ticksSinceRequest = 0;
            pendingAction = reconnect ? PendingAction.RECONNECT : PendingAction.INITIAL_CONNECT;
            LOGGER.info(reconnect ? "HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED run={} attempt={} state={} thread={}"
                    : "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED run={} attempt={} state={} thread={}",
                    runId(), attemptId(), state, Thread.currentThread().getName());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IOException) {
                LOGGER.warn("HANDSHAKE_ACCEPTANCE_CLIENT_CONTROL_FILE_DELETE_RETRY run={} attempt={} requestFile={}",
                        runId(), attemptId(), request.getFileName(), exception);
            } else {
                fail("HANDSHAKE_ACCEPTANCE_CLIENT_FAILED", exception);
            }
        }
    }

    private void invokeConnection(Minecraft minecraft, boolean reconnect) {
        String host = System.getenv("PARTIALRELOAD_ACCEPTANCE_HOST");
        String port = System.getenv("PARTIALRELOAD_ACCEPTANCE_PORT");
        LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_CALL_ENTER run={} attempt={} thread={} screen={} host={} port={} state={}",
                runId(), attemptId(), Thread.currentThread().getName(),
                minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName(), host, port, state);
        try {
            startConnection(minecraft);
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_CALL_RETURN run={} attempt={} thread={} screen={} host={} port={} state={}",
                    runId(), attemptId(), Thread.currentThread().getName(),
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName(), host, port, state);
        } catch (RuntimeException exception) {
            state = AcceptanceClientState.FAILED;
            LOGGER.error("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_CALL_FAILED run={} attempt={} thread={} screen={} host={} port={} state={}",
                    runId(), attemptId(), Thread.currentThread().getName(),
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName(), host, port, state, exception);
            throw exception;
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
            LOGGER.info("HANDSHAKE_ACCEPTANCE_CLIENT_ACCESSIBILITY_DISMISSED run={} attempt={}", runId(), attemptId());
        }
    }

    private static boolean isReconnectReady(Minecraft minecraft) {
        return minecraft.getOverlay() == null && minecraft.getConnection() == null
                && minecraft.level == null && minecraft.screen != null;
    }

    private Path controlDirectory() {
        return Path.of(System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_CONTROL_DIR", "."));
    }

    private static String runId() {
        return System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_RUN_ID", "-");
    }

    private static String attemptId() {
        return System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_ATTEMPT_ID", "-");
    }

    private static String initialConnectMode() {
        return System.getenv().getOrDefault("PARTIALRELOAD_ACCEPTANCE_INITIAL_CONNECT_MODE", "CONTROL");
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
