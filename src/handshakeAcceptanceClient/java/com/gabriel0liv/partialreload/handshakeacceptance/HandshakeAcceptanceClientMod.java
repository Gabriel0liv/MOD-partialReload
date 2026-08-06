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
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
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
    private boolean nonInventoryContainerObserved;
    private AdvancementObserver advancementObserver;

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
        observeContainerLifecycle(minecraft);
        if (minecraft.getConnection() != null && Files.exists(control.resolve("open-container.request"))) {
            openContainer(minecraft, control.resolve("open-container.request"));
            return;
        }
        if (minecraft.getConnection() != null && Files.exists(control.resolve("probe-data.request"))) {
            probeData(minecraft, control.resolve("probe-data.request"));
            return;
        }
        if (minecraft.getConnection() != null && Files.exists(control.resolve("select-advancement.request"))) {
            selectAdvancement(minecraft, control.resolve("select-advancement.request"));
            return;
        }
        if (minecraft.getConnection() != null && Files.exists(control.resolve("probe-advancements.request"))) {
            probeAdvancements(minecraft, control.resolve("probe-advancements.request"));
            return;
        }
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

    private void observeContainerLifecycle(Minecraft minecraft) {
        if (minecraft.player == null) return;
        boolean nonInventory = minecraft.player.containerMenu != minecraft.player.inventoryMenu;
        if (nonInventory && !nonInventoryContainerObserved) {
            nonInventoryContainerObserved = true;
            LOGGER.info("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_OPENED run={} attempt={} menu={} screen={}",
                    runId(), attemptId(), minecraft.player.containerMenu.getClass().getSimpleName(),
                    minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName());
        } else if (!nonInventory && nonInventoryContainerObserved) {
            nonInventoryContainerObserved = false;
            LOGGER.info("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_CLOSED run={} attempt={} screen={}",
                    runId(), attemptId(), minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName());
        }
    }

    private void openContainer(Minecraft minecraft, Path request) {
        try {
            Files.deleteIfExists(request);
            if (minecraft.player == null || minecraft.gameMode == null) {
                throw new IllegalStateException("client player/game mode unavailable");
            }
            var target = minecraft.player.blockPosition().east();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.WEST, target, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
            LOGGER.info("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_OPEN_REQUESTED run={} attempt={} target={}",
                    runId(), attemptId(), target);
        } catch (IOException | RuntimeException exception) {
            fail("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_OPEN_FAILED", exception);
        }
    }

    private void probeData(Minecraft minecraft, Path request) {
        try {
            Files.deleteIfExists(request);
            if (minecraft.getConnection() == null || minecraft.level == null) {
                throw new IllegalStateException("client connection unavailable");
            }
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("partialreload_test", "acceptance");
            var recipe = minecraft.getConnection().getRecipeManager().byKey(recipeId);
            int resultCount = recipe.map(value -> value.getResultItem(minecraft.level.registryAccess()).getCount()).orElse(-1);
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("partialreload_test", "joint"));
            String members = BuiltInRegistries.ITEM.getTag(tag).map(named -> named.stream()
                    .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).sorted()
                    .collect(java.util.stream.Collectors.joining(","))).orElse("<missing>");
            LOGGER.info("DEFERRED_REFRESH_ACCEPTANCE_CLIENT_DATA run={} attempt={} recipeResultCount={} tagMembers={}",
                    runId(), attemptId(), resultCount, members);
        } catch (IOException | RuntimeException exception) {
            fail("DEFERRED_REFRESH_ACCEPTANCE_CLIENT_DATA_FAILED", exception);
        }
    }

    private void selectAdvancement(Minecraft minecraft, Path request) {
        try {
            Files.deleteIfExists(request);
            if (minecraft.getConnection() == null) throw new IllegalStateException("client connection unavailable");
            var advancements = minecraft.getConnection().getAdvancements();
            Advancement root = advancements.getAdvancements().get(
                    ResourceLocation.fromNamespaceAndPath("partialreload_advancement", "root"));
            if (root == null) throw new IllegalStateException("acceptance advancement root absent");
            advancements.setSelectedTab(root, true);
            LOGGER.info("ADVANCEMENT_ACCEPTANCE_CLIENT_TAB_SELECTED run={} attempt={} selected={}",
                    runId(), attemptId(), root.getId());
        } catch (IOException | RuntimeException exception) {
            fail("ADVANCEMENT_ACCEPTANCE_CLIENT_TAB_SELECT_FAILED", exception);
        }
    }

    private void probeAdvancements(Minecraft minecraft, Path request) {
        try {
            Files.deleteIfExists(request);
            if (minecraft.getConnection() == null) throw new IllegalStateException("client connection unavailable");
            advancementObserver = new AdvancementObserver();
            minecraft.getConnection().getAdvancements().setListener(advancementObserver);
            ResourceLocation child = ResourceLocation.fromNamespaceAndPath("partialreload_advancement", "child");
            AdvancementProgress progress = advancementObserver.progress.get(child);
            String completed = progress == null ? "<none>" : java.util.stream.StreamSupport
                    .stream(progress.getCompletedCriteria().spliterator(), false).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            String remaining = progress == null ? "<none>" : java.util.stream.StreamSupport
                    .stream(progress.getRemainingCriteria().spliterator(), false).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            String ids = advancementObserver.ids.stream().map(ResourceLocation::toString).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            LOGGER.info("ADVANCEMENT_ACCEPTANCE_CLIENT_STATE run={} attempt={} ids={} childCompleted={} childRemaining={} selected={}",
                    runId(), attemptId(), ids, completed, remaining,
                    advancementObserver.selected == null ? "<none>" : advancementObserver.selected);
        } catch (IOException | RuntimeException exception) {
            fail("ADVANCEMENT_ACCEPTANCE_CLIENT_PROBE_FAILED", exception);
        }
    }

    private static final class AdvancementObserver implements ClientAdvancements.Listener {
        private final java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        private final java.util.Map<ResourceLocation, AdvancementProgress> progress = new java.util.LinkedHashMap<>();
        private ResourceLocation selected;
        @Override public void onAddAdvancementRoot(Advancement value) { ids.add(value.getId()); }
        @Override public void onRemoveAdvancementRoot(Advancement value) { ids.remove(value.getId()); }
        @Override public void onAddAdvancementTask(Advancement value) { ids.add(value.getId()); }
        @Override public void onRemoveAdvancementTask(Advancement value) { ids.remove(value.getId()); }
        @Override public void onAdvancementsCleared() { ids.clear(); progress.clear(); selected = null; }
        @Override public void onUpdateAdvancementProgress(Advancement value, AdvancementProgress state) {
            progress.put(value.getId(), state);
        }
        @Override public void onSelectedTabChanged(Advancement value) {
            selected = value == null ? null : value.getId();
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
