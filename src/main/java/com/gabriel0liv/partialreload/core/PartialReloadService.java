package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.PartialReloadException;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.api.ScanResult;
import com.gabriel0liv.partialreload.change.ChangeDetector;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.change.ResourceChange;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.PreparedFunctions;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.function.FunctionCommitCompatibility;
import com.gabriel0liv.partialreload.function.FunctionCommitPolicy;
import com.gabriel0liv.partialreload.function.FunctionCommitTransaction;
import com.gabriel0liv.partialreload.function.FunctionGeneration;
import com.gabriel0liv.partialreload.function.FunctionLibraryBridge;
import com.gabriel0liv.partialreload.function.FunctionTransactionStatus;
import com.gabriel0liv.partialreload.function.TransactionEventType;
import com.gabriel0liv.partialreload.loot.LootPreparationContext;
import com.gabriel0liv.partialreload.loot.PreparedLootData;
import com.gabriel0liv.partialreload.loot.VanillaLootDataProvider;
import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;
import com.gabriel0liv.partialreload.loot.LootDataCommitTransaction;
import com.gabriel0liv.partialreload.loot.LootDataFaultInjection;
import com.gabriel0liv.partialreload.loot.LootDataFaultPoint;
import com.gabriel0liv.partialreload.loot.LootDataManagerBridge;
import com.gabriel0liv.partialreload.loot.LootDataTransactionStatus;
import com.gabriel0liv.partialreload.glm.ActiveGlobalLootModifierGeneration;
import com.gabriel0liv.partialreload.glm.ForgeGlobalLootModifierProvider;
import com.gabriel0liv.partialreload.glm.GlobalLootModifierCommitTransaction;
import com.gabriel0liv.partialreload.glm.GlobalLootModifierFaultInjection;
import com.gabriel0liv.partialreload.glm.GlobalLootModifierFaultPoint;
import com.gabriel0liv.partialreload.glm.GlobalLootModifierPreparationContext;
import com.gabriel0liv.partialreload.glm.GlobalLootModifierTransactionStatus;
import com.gabriel0liv.partialreload.glm.LootAndGlobalModifiersCommitTransaction;
import com.gabriel0liv.partialreload.glm.LootModifierManagerBridge;
import com.gabriel0liv.partialreload.glm.PreparedGlobalLootModifiers;
import com.gabriel0liv.partialreload.glm.PreparedLootAndGlobalModifiers;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.recipe.PreparedRecipe;
import com.gabriel0liv.partialreload.recipe.VanillaRecipesProvider;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.tags.VanillaTagsProvider;
import com.gabriel0liv.partialreload.tags.PreparedTagsResolutionView;
import com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipes;
import com.gabriel0liv.partialreload.joint.TagRecipeDependencyGraph;
import com.gabriel0liv.partialreload.joint.TagRecipeDelta;
import com.gabriel0liv.partialreload.joint.ActiveTagRecipeGeneration;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitTransaction;
import com.gabriel0liv.partialreload.joint.TagRecipeTransactionStatus;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitCompatibility;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultInjection;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultPoint;
import com.gabriel0liv.partialreload.joint.TagRecipeConnectedPlayerPolicy;
import com.gabriel0liv.partialreload.joint.DeferredClientRefreshTracker;
import com.gabriel0liv.partialreload.tags.ActiveTagGeneration;
import com.gabriel0liv.partialreload.recipe.ActiveRecipeGeneration;
import com.gabriel0liv.partialreload.recipe.ActiveRecipeSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.resource.ResourceScanException;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.time.Instant;
import java.time.Clock;
import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.TagsUpdatedEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class PartialReloadService {
    private final ProviderRegistry providerRegistry;
    private final ReloadProvider scannerProvider;
    private final ReloadPlanner planner;
    private final VanillaFunctionsProvider functionsProvider;
    private final VanillaLootDataProvider lootDataProvider;
    private final ForgeGlobalLootModifierProvider globalLootModifierProvider;
    private final VanillaRecipesProvider recipesProvider = new VanillaRecipesProvider();
    private final VanillaTagsProvider tagsProvider = new VanillaTagsProvider();
    private final PartialReloadStateMachine stateMachine = new PartialReloadStateMachine();
    private volatile ConnectedPlayerProbe connectedPlayerProbe = ConnectedPlayerProbe.DEFAULT;
    private volatile DeferredPlayerSessionProbe deferredPlayerSessionProbe = DeferredPlayerSessionProbe.DEFAULT;
    private volatile boolean tagRecipeSafePointHeld;
    private volatile TagRecipeCurrentResourceProbe currentResourceProbe = this::currentResourcesMatchReal;
    private final DeferredClientRefreshTracker deferredClientRefreshTracker = new DeferredClientRefreshTracker();

    public void connectedPlayerProbe(ConnectedPlayerProbe probe) {
        connectedPlayerProbe = Objects.requireNonNull(probe, "probe");
    }

    public void resetConnectedPlayerProbe() {
        connectedPlayerProbe = ConnectedPlayerProbe.DEFAULT;
    }

    public void deferredPlayerSessionProbe(DeferredPlayerSessionProbe probe) {
        requireGameTestAccess();
        deferredPlayerSessionProbe = Objects.requireNonNull(probe, "probe");
    }

    public void resetDeferredPlayerSessionProbe() {
        deferredPlayerSessionProbe = DeferredPlayerSessionProbe.DEFAULT;
    }

    /** Userdev-only seam control; production always uses the real player list. */
    public void fixedConnectedPlayerProbe(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        connectedPlayerProbe = server -> count;
    }

    public void holdTagRecipeSafePoint() { tagRecipeSafePointHeld = true; }
    public void releaseTagRecipeSafePoint() { tagRecipeSafePointHeld = false; }

    private static void requireGameTestAccess() {
        if (FMLEnvironment.production) throw new IllegalStateException("TAG_RECIPE_GAMETEST_ACCESS_NOT_AVAILABLE_IN_PRODUCTION");
    }

    public synchronized TagRecipeGameTestState captureTagRecipeGameTestState() {
        requireGameTestAccess();
        return new TagRecipeGameTestState(activeReference, latestScan, lastChangeSet, lastPlan, lastError,
                preparedArtifact, tagRecipeTransaction, retainedTagRecipeGeneration, activeTagRecipeGeneration,
                stateMachine.state(), connectedPlayerProbe, tagRecipeSafePointHeld, currentResourceProbe,
                deferredPlayerSessionProbe, deferredClientRefreshTracker.snapshot());
    }

    public synchronized void installTagRecipeGameTestReadyState(ResourceSnapshot activeSnapshot, ResourceSnapshot candidateSnapshot,
                                                                  ChangeSet changeSet, PreparedTagsAndRecipes artifact,
                                                                  TagRecipeCurrentResourceProbe resourceProbe) {
        requireGameTestAccess();
        if (artifact == null || artifact.sourceSnapshot() != candidateSnapshot || !artifact.isApplicable())
            throw new IllegalArgumentException("TAG_RECIPE_GAMETEST_ARTIFACT_INVALID");
        activeReference = activeSnapshot; latestScan = candidateSnapshot; lastChangeSet = changeSet;
        lastPlan = null; lastError = null; preparedArtifact = artifact; tagRecipeTransaction = null;
        retainedTagRecipeGeneration = null; activeTagRecipeGeneration = null;
        connectedPlayerProbe = ConnectedPlayerProbe.DEFAULT; tagRecipeSafePointHeld = false;
        deferredPlayerSessionProbe = DeferredPlayerSessionProbe.DEFAULT;
        deferredClientRefreshTracker.clear();
        currentResourceProbe = Objects.requireNonNull(resourceProbe, "resourceProbe");
        stateMachine.forceStateForGameTest(PartialReloadState.READY);
    }

    public synchronized void restoreTagRecipeGameTestState(TagRecipeGameTestState snapshot) {
        requireGameTestAccess();
        activeReference = snapshot.activeReference(); latestScan = snapshot.latestScan(); lastChangeSet = snapshot.lastChangeSet();
        lastPlan = snapshot.lastPlan(); lastError = snapshot.lastError(); preparedArtifact = snapshot.preparedArtifact();
        tagRecipeTransaction = snapshot.tagRecipeTransaction(); retainedTagRecipeGeneration = snapshot.retainedTagRecipeGeneration();
        activeTagRecipeGeneration = snapshot.activeTagRecipeGeneration(); connectedPlayerProbe = snapshot.connectedPlayerProbe();
        tagRecipeSafePointHeld = snapshot.safePointHeld(); currentResourceProbe = snapshot.currentResourceProbe();
        deferredPlayerSessionProbe = snapshot.deferredPlayerSessionProbe();
        deferredClientRefreshTracker.restore(snapshot.deferredClientRefreshSnapshot());
        stateMachine.forceStateForGameTest(snapshot.state());
    }

    public synchronized ActiveTagRecipeGeneration captureTagRecipeGenerationForGameTest(MinecraftServer server, PreparedTagsAndRecipes candidate) {
        requireGameTestAccess();
        Set<ResourceKey<? extends Registry<?>>> scope = new LinkedHashSet<>();
        for (String path : candidate.preparedTags().registries().keySet()) {
            String canonical = canonicalRegistry(path);
            if (canonical != null) scope.add(registryKey(canonical));
        }
        return captureTagRecipeGeneration(server, scope, candidate.preparedTags());
    }

    public synchronized void restoreTagRecipeGenerationForGameTest(MinecraftServer server, ActiveTagRecipeGeneration generation) {
        requireGameTestAccess();
        if (generation == null) throw new IllegalArgumentException("generation");
        stateMachine.forceStateForGameTest(PartialReloadState.COMMITTING);
        TagRecipeCommitTransaction tx = new TagRecipeCommitTransaction(UUID.randomUUID(), null, Instant.now(), "gametest-restore");
        tx.previousGeneration(generation);
        tx.registriesToMutate(generation.tags().registries().keySet());
        generation.tags().registries().keySet().forEach(tx::tagRegistryMutated);
        restoreTagRecipeGeneration(server, generation, tx);
    }

    @Nullable
    private ResourceSnapshot activeReference;
    @Nullable
    private ResourceSnapshot latestScan;
    private ChangeSet lastChangeSet = new ChangeSet(java.util.List.of());
    @Nullable
    private ReloadPlan lastPlan;
    @Nullable
    private String lastError;
    @Nullable
    private PreparedReloadArtifact preparedArtifact;
    @Nullable
    private FunctionCommitTransaction transaction;
    @Nullable
    private FunctionGeneration retainedGeneration;
    @Nullable
    private FunctionGeneration activeGeneration;
    @Nullable private TagRecipeCommitTransaction tagRecipeTransaction;
    @Nullable private ActiveTagRecipeGeneration retainedTagRecipeGeneration;
    @Nullable private ActiveTagRecipeGeneration activeTagRecipeGeneration;
    @Nullable private LootDataCommitTransaction lootDataTransaction;
    @Nullable private ActiveLootDataGeneration retainedLootDataGeneration;
    @Nullable private ActiveLootDataGeneration activeLootDataGeneration;
    @Nullable private ResourceSnapshot retainedLootDataSourceSnapshot;
    @Nullable private GlobalLootModifierCommitTransaction globalLootModifierTransaction;
    @Nullable private ActiveGlobalLootModifierGeneration retainedGlobalLootModifierGeneration;
    @Nullable private ActiveGlobalLootModifierGeneration activeGlobalLootModifierGeneration;
    @Nullable private LootAndGlobalModifiersCommitTransaction lootAndGlmTransaction;
    @Nullable private ActiveLootDataGeneration retainedJointLootGeneration;
    @Nullable private ActiveGlobalLootModifierGeneration retainedJointGlmGeneration;
    @Nullable private ResourceSnapshot retainedJointSourceSnapshot;

    public PartialReloadService(
            ProviderRegistry providerRegistry,
            ReloadProvider scannerProvider,
            ReloadPlanner planner,
            VanillaFunctionsProvider functionsProvider,
            VanillaLootDataProvider lootDataProvider,
            ForgeGlobalLootModifierProvider globalLootModifierProvider
    ) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.scannerProvider = Objects.requireNonNull(scannerProvider, "scannerProvider");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.functionsProvider = Objects.requireNonNull(functionsProvider, "functionsProvider");
        this.lootDataProvider = Objects.requireNonNull(lootDataProvider, "lootDataProvider");
        this.globalLootModifierProvider = Objects.requireNonNull(globalLootModifierProvider, "globalLootModifierProvider");
    }

    /** Compatibility constructor for isolated lifecycle tests. */
    public PartialReloadService(ProviderRegistry providerRegistry, ReloadProvider scannerProvider,
                                ReloadPlanner planner, VanillaFunctionsProvider functionsProvider,
                                VanillaLootDataProvider lootDataProvider) {
        this(providerRegistry, scannerProvider, planner, functionsProvider, lootDataProvider,
                new ForgeGlobalLootModifierProvider(new ResourceScanner(Clock.systemUTC())));
    }

    public CompletableFuture<ScanResult> scanAsync(ScanContext context, Executor background, Executor owner) {
        synchronized (this) {
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.SCANNING);
            lastError = null;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return scannerProvider.scan(context);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, background).handleAsync((result, throwable) -> {
            synchronized (this) {
                if (throwable != null) {
                    lastError = rootMessage(throwable);
                    stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
                    throw new CompletionException(throwable);
                }
                latestScan = result.snapshot();
                if (activeReference == null) {
                    activeReference = result.snapshot();
                }
                lastChangeSet = ChangeDetector.diff(activeReference, latestScan);
                stateMachine.transitionTo(
                        preparedArtifact == null ? PartialReloadState.IDLE : PartialReloadState.READY
                );
                return result;
            }
        }, owner);
    }

    public synchronized ReloadPlan planChanged() {
        return plan(lastChangeSet.onlyChanged());
    }

    public synchronized ReloadPlan planCategory(ReloadCategory category) {
        return plan(lastChangeSet.onlyChanged().forCategory(category));
    }

    public CompletableFuture<PreparedFunctions> prepareFunctionsAsync(
            FunctionPreparationContext context,
            Executor background,
            Executor owner
    ) {
        synchronized (this) {
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null;
            lastError = null;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return functionsProvider.prepare(context);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, background).handleAsync((artifact, throwable) -> {
            synchronized (this) {
                if (throwable != null) {
                    lastError = rootMessage(throwable);
                    stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
                    throw new CompletionException(throwable);
                }
                stateMachine.transitionTo(PartialReloadState.VALIDATING);
                preparedArtifact = artifact;
                lastError = null;
                stateMachine.transitionTo(PartialReloadState.READY);
                return artifact;
            }
        }, owner);
    }

    public synchronized boolean hasFunctionChanges() {
        return lastChangeSet.changedResources().stream()
                .anyMatch(change -> change.category() == ReloadCategory.FUNCTIONS);
    }

    public synchronized PreparedFunctions preparedFunctions() {
        return preparedArtifact instanceof PreparedFunctions functions ? functions : null;
    }

    public CompletableFuture<PreparedLootData> prepareLootDataAsync(
            LootPreparationContext context,
            Executor background,
            Executor owner
    ) {
        synchronized (this) {
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null;
            lastError = null;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lootDataProvider.prepare(context);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, background).handleAsync((artifact, throwable) -> {
            synchronized (this) {
                if (throwable != null) {
                    lastError = rootMessage(throwable);
                    stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
                    throw new CompletionException(throwable);
                }
                stateMachine.transitionTo(PartialReloadState.VALIDATING);
                preparedArtifact = artifact;
                lastError = null;
                stateMachine.transitionTo(PartialReloadState.READY);
                return artifact;
            }
        }, owner);
    }

    public CompletableFuture<PreparedGlobalLootModifiers> prepareGlobalLootModifiersAsync(
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            Executor background, Executor owner) {
        synchronized (this) {
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null;
            lastError = null;
        }
        ResourceSnapshot baseline;
        synchronized (this) { baseline = activeReference; }
        List<net.minecraft.resources.ResourceLocation> activeGlmOrder = List.copyOf(
                LootModifierManagerBridge.capture(LootModifierManagerBridge.activeManager())
                        .orderedModifiers().keySet());
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResourceSnapshot snapshot = scanCurrent(resourceManager);
                return globalLootModifierProvider.prepare(new GlobalLootModifierPreparationContext(
                        resourceManager, snapshot, baseline, activeGlmOrder,
                        Clock.systemUTC(), UUID::randomUUID));
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, background).handleAsync((artifact, throwable) -> finishPreparation(artifact, throwable), owner);
    }

    public CompletableFuture<PreparedLootAndGlobalModifiers> prepareLootAndGlobalModifiersAsync(
            LootPreparationContext lootContext, Executor background, Executor owner) {
        synchronized (this) {
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null;
            lastError = null;
        }
        List<net.minecraft.resources.ResourceLocation> activeGlmOrder = List.copyOf(
                LootModifierManagerBridge.capture(LootModifierManagerBridge.activeManager())
                        .orderedModifiers().keySet());
        return CompletableFuture.supplyAsync(() -> {
            try {
                PreparedLootData loot = lootDataProvider.prepare(lootContext);
                ResourceSnapshot full = scanCurrent(lootContext.resourceManager());
                PreparedLootData sharedLoot = new PreparedLootData(loot.preparationId(), loot.createdAt(), full,
                        loot.requestedCategories(), loot.predicates(), loot.itemModifiers(), loot.lootTables(),
                        loot.dependencyGraph(), loot.delta(), loot.validation());
                PreparedGlobalLootModifiers glm = globalLootModifierProvider.prepare(
                        new GlobalLootModifierPreparationContext(lootContext.resourceManager(), full,
                                lootContext.activeReference(), activeGlmOrder,
                                lootContext.clock(), lootContext.idSupplier()));
                return new PreparedLootAndGlobalModifiers(UUID.randomUUID(), Instant.now(lootContext.clock()),
                        full, sharedLoot, glm, null);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, background).handleAsync((artifact, throwable) -> finishPreparation(artifact, throwable), owner);
    }

    private synchronized <T extends PreparedReloadArtifact> T finishPreparation(T artifact, Throwable throwable) {
        if (throwable != null) {
            lastError = rootMessage(throwable);
            stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
            throw new CompletionException(throwable);
        }
        stateMachine.transitionTo(PartialReloadState.VALIDATING);
        preparedArtifact = artifact;
        lastError = null;
        stateMachine.transitionTo(PartialReloadState.READY);
        return artifact;
    }

    private ResourceSnapshot scanCurrent(net.minecraft.server.packs.resources.ResourceManager resourceManager)
            throws PartialReloadException {
        return scannerProvider.scan(new ScanContext(resourceManager, PartialReloadConfig.maxScannedResources(),
                java.time.Duration.ofSeconds(PartialReloadConfig.scanTimeoutSeconds()), true)).snapshot();
    }

    public synchronized boolean hasLootDataChanges() {
        return lastChangeSet.changedResources().stream()
                .anyMatch(change -> PreparedLootData.COMPLETE_SCOPE.contains(change.category()));
    }

    public synchronized boolean hasGlobalLootModifierChanges() {
        return lastChangeSet.changedResources().stream()
                .anyMatch(change -> change.category() == ReloadCategory.GLOBAL_LOOT_MODIFIERS);
    }

    public synchronized boolean hasMixedFunctionAndLootChanges() {
        return hasFunctionChanges() && hasLootDataChanges();
    }

    public synchronized PreparedLootData preparedLootData() {
        return preparedArtifact instanceof PreparedLootData lootData ? lootData : null;
    }

    public synchronized PreparedGlobalLootModifiers preparedGlobalLootModifiers() {
        return preparedArtifact instanceof PreparedGlobalLootModifiers value ? value : null;
    }

    public synchronized PreparedLootAndGlobalModifiers preparedLootAndGlobalModifiers() {
        return preparedArtifact instanceof PreparedLootAndGlobalModifiers value ? value : null;
    }

    public synchronized PreparedRecipes preparedRecipes() {
        return preparedArtifact instanceof PreparedRecipes recipes ? recipes : null;
    }

    public synchronized boolean hasRecipeChanges() {
        return lastChangeSet.changedResources().stream().anyMatch(change -> change.category() == ReloadCategory.RECIPES);
    }

    public synchronized PreparedTags preparedTags() {
        return preparedArtifact instanceof PreparedTags tags ? tags : null;
    }

    public synchronized PreparedTagsAndRecipes preparedTagsAndRecipes() {
        return preparedArtifact instanceof PreparedTagsAndRecipes joint ? joint : null;
    }

    public synchronized PartialReloadState state() {
        return stateMachine.state();
    }

    public synchronized boolean tagRecipeSafePointHeldForGameTest() {
        requireGameTestAccess();
        return tagRecipeSafePointHeld;
    }

    public synchronized boolean hasTagChanges() {
        return lastChangeSet.changedResources().stream().anyMatch(change -> change.category() == ReloadCategory.TAGS);
    }

    public CompletableFuture<PreparedTags> prepareTagsAsync(net.minecraft.server.packs.resources.ResourceManager resourceManager,
                                                            net.minecraft.core.RegistryAccess registryAccess,
                                                            Executor background, Executor owner) {
        ResourceSnapshot snapshot; ResourceSnapshot baseline;
        synchronized (this) {
            snapshot = latestScan;
            if (snapshot == null) throw new IllegalStateException("TAG_PREPARATION_REQUIRED: scan first");
            resetTerminalState(); stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null; lastError = null; baseline = activeReference;
        }
        return CompletableFuture.supplyAsync(() -> tagsProvider.prepare(resourceManager, registryAccess, snapshot, baseline,
                PartialReloadConfig.maxTagFiles(), PartialReloadConfig.maxTags(), PartialReloadConfig.maxTagEntries(),
                PartialReloadConfig.maxTagJsonBytes(), java.time.Duration.ofSeconds(PartialReloadConfig.tagPrepareTimeoutSeconds()).toNanos(), UUID.randomUUID()), background)
                .handleAsync((artifact, throwable) -> {
                    synchronized (this) {
                        if (throwable != null) { lastError = rootMessage(throwable); stateMachine.transitionTo(PartialReloadState.FAILED_SAFE); throw new CompletionException(throwable); }
                        stateMachine.transitionTo(PartialReloadState.VALIDATING); preparedArtifact = artifact; stateMachine.transitionTo(PartialReloadState.READY); return artifact;
                    }
                }, owner);
    }

    public synchronized boolean hasMixedRecipeChanges() {
        return lastChangeSet.changedResources().stream().map(ResourceChange::category)
                .distinct().anyMatch(category -> category != ReloadCategory.RECIPES);
    }

    public CompletableFuture<PreparedRecipes> prepareRecipesAsync(
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            Executor background, Executor owner) {
        ResourceSnapshot snapshot;
        ResourceSnapshot baseline;
        Set<ResourceLocation> changedTags;
        synchronized (this) {
            snapshot = latestScan;
            if (snapshot == null) throw new IllegalStateException("RECIPE_PREPARATION_REQUIRED: scan first");
            resetTerminalState();
            stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null; lastError = null;
            baseline = activeReference;
            changedTags = lastChangeSet.changedResources().stream()
                    .filter(change -> change.category() == ReloadCategory.TAGS)
                    .map(ResourceChange::location).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return CompletableFuture.supplyAsync(() -> recipesProvider.prepare(resourceManager, snapshot, baseline,
                changedTags, PartialReloadConfig.maxRecipes(), PartialReloadConfig.maxRecipeJsonBytes(),
                java.time.Duration.ofSeconds(60).toNanos(), UUID.randomUUID()), background)
                .handleAsync((artifact, throwable) -> {
                    synchronized (this) {
                        if (throwable != null) { lastError = rootMessage(throwable); stateMachine.transitionTo(PartialReloadState.FAILED_SAFE); throw new CompletionException(throwable); }
                        preparedArtifact = artifact; stateMachine.transitionTo(PartialReloadState.VALIDATING); stateMachine.transitionTo(PartialReloadState.READY); return artifact;
                    }
                }, owner);
    }

    public CompletableFuture<PreparedTagsAndRecipes> prepareTagsAndRecipesAsync(
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            net.minecraft.core.RegistryAccess registryAccess, Executor background, Executor owner) {
        ResourceSnapshot snapshot; ResourceSnapshot baseline;
        synchronized (this) {
            snapshot = latestScan;
            if (snapshot == null) throw new IllegalStateException("JOINT_PREPARATION_REQUIRED: scan first");
            resetTerminalState(); stateMachine.transitionTo(PartialReloadState.PREPARING);
            preparedArtifact = null; lastError = null; baseline = activeReference;
        }
        final ResourceSnapshot shared = snapshot;
        return CompletableFuture.supplyAsync(() -> {
            PreparedTags tags = tagsProvider.prepare(resourceManager, registryAccess, shared, baseline,
                    PartialReloadConfig.maxTagFiles(), PartialReloadConfig.maxTags(), PartialReloadConfig.maxTagEntries(),
                    PartialReloadConfig.maxTagJsonBytes(), java.time.Duration.ofSeconds(PartialReloadConfig.tagPrepareTimeoutSeconds()).toNanos(), UUID.randomUUID());
            if (!tags.isApplicable()) throw new IllegalStateException("JOINT_TAG_RECIPE_PREPARATION_FAILED: tag candidate is invalid");
            Set<ResourceLocation> changedTagIds = new java.util.LinkedHashSet<>(tags.delta().tagsAdded());
            changedTagIds.addAll(tags.delta().tagsModified()); changedTagIds.addAll(tags.delta().tagsRemoved());
            PreparedRecipes recipes = recipesProvider.prepareWithCandidateTags(resourceManager, shared, baseline,
                    new PreparedTagsResolutionView(tags), changedTagIds, PartialReloadConfig.maxRecipes(), PartialReloadConfig.maxRecipeJsonBytes(),
                    java.time.Duration.ofSeconds(60).toNanos(), UUID.randomUUID());
            return com.gabriel0liv.partialreload.joint.PreparedTagsAndRecipesFactory.combine(UUID.randomUUID(), shared, tags, recipes);
        }, background).handleAsync((artifact, throwable) -> {
            synchronized (this) {
                if (throwable != null) { lastError = rootMessage(throwable); stateMachine.transitionTo(PartialReloadState.FAILED_SAFE); throw new CompletionException(throwable); }
                stateMachine.transitionTo(PartialReloadState.VALIDATING); preparedArtifact = artifact;
                if (!artifact.isApplicable()) lastError = "JOINT_TAG_RECIPE_PREPARATION_FAILED";
                stateMachine.transitionTo(PartialReloadState.READY); return artifact;
            }
        }, owner);
    }

    public synchronized PreparedReloadArtifact preparedArtifact() {
        return preparedArtifact;
    }

    public synchronized FunctionCommitCompatibility functionCommitCompatibility(MinecraftServer server) {
        return FunctionCommitCompatibility.inspect(server);
    }

    public synchronized FunctionCommitTransaction requestFunctionCommit(
            MinecraftServer server, String requester) {
        if (stateMachine.state() == PartialReloadState.DEGRADED) {
            throw new IllegalStateException("FUNCTION_COMMIT_DEGRADED: restart is required");
        }
        if (stateMachine.state() != PartialReloadState.READY) {
            throw new IllegalStateException("FUNCTION_PREPARATION_REQUIRED: state must be READY");
        }
        if (transaction != null && transaction.status() != FunctionTransactionStatus.SUCCESS
                && transaction.status() != FunctionTransactionStatus.ROLLED_BACK
                && transaction.status() != FunctionTransactionStatus.FAILED_SAFE
                && transaction.status() != FunctionTransactionStatus.DEGRADED) {
            throw new IllegalStateException("FUNCTION_TRANSACTION_ALREADY_RUNNING");
        }
        if (!(preparedArtifact instanceof PreparedFunctions functions)) {
            if (preparedArtifact instanceof PreparedLootData) {
                throw new IllegalArgumentException("Commit is not implemented for loot data. Prepared artifact remains unchanged.");
            }
            if (preparedArtifact instanceof PreparedRecipes) {
                throw new IllegalArgumentException("Commit is not implemented for recipes. Prepared artifact remains unchanged.");
            }
            if (preparedArtifact instanceof PreparedTags) {
                throw new IllegalArgumentException("Commit is not implemented for tags. Prepared artifact remains unchanged.");
            }
            if (preparedArtifact instanceof PreparedTagsAndRecipes) {
                throw new IllegalArgumentException("Commit is not implemented for joint tag and recipe candidates. Prepared artifact remains unchanged.");
            }
            throw new IllegalArgumentException("FUNCTION_PREPARATION_REQUIRED");
        }
        if (!functions.isApplicable()) throw new IllegalArgumentException("FUNCTION_PREPARATION_INVALID");
        FunctionCommitCompatibility compatibility = FunctionCommitCompatibility.inspect(server);
        if (!compatibility.compatible()) throw new IllegalStateException(
                "FUNCTION_COMMIT_NOT_COMPATIBLE: " + compatibility.detail());
        FunctionCommitTransaction next = new FunctionCommitTransaction(
                UUID.randomUUID(), functions.preparationId(), Instant.now(), requester,
                FunctionCommitPolicy.DO_NOT_RUN);
        next.event(Instant.now(), TransactionEventType.REQUESTED, "apply prepared");
        next.event(Instant.now(), TransactionEventType.VALIDATED, compatibility.detail());
        next.status(FunctionTransactionStatus.QUIESCING);
        next.event(Instant.now(), TransactionEventType.QUEUED, "server tick safe point");
        transaction = next;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return next;
    }

    public synchronized FunctionCommitTransaction requestManualRollback(String requester) {
        if (retainedGeneration == null) throw new IllegalStateException("FUNCTION_MANUAL_ROLLBACK_UNAVAILABLE");
        if (stateMachine.state() != PartialReloadState.IDLE
                && stateMachine.state() != PartialReloadState.SUCCESS) {
            throw new IllegalStateException("FUNCTION_TRANSACTION_ALREADY_RUNNING");
        }
        if (transaction != null && transaction.status() == FunctionTransactionStatus.QUIESCING) {
            throw new IllegalStateException("FUNCTION_TRANSACTION_ALREADY_RUNNING");
        }
        FunctionCommitTransaction next = new FunctionCommitTransaction(
                UUID.randomUUID(), null, Instant.now(), requester, FunctionCommitPolicy.DO_NOT_RUN);
        next.previousGeneration(retainedGeneration);
        next.status(FunctionTransactionStatus.QUIESCING);
        next.event(Instant.now(), TransactionEventType.REQUESTED, "manual rollback");
        transaction = next;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return next;
    }

    public synchronized FunctionCommitTransaction transaction() { return transaction; }
    public synchronized FunctionGeneration retainedGeneration() { return retainedGeneration; }

    public synchronized TagRecipeCommitCompatibility tagRecipeCommitCompatibility(MinecraftServer server) {
        return TagRecipeCommitCompatibility.inspect(server);
    }

    public synchronized TagRecipeCommitTransaction tagRecipeTransaction() { return tagRecipeTransaction; }
    public synchronized ActiveTagRecipeGeneration retainedTagRecipeGeneration() { return retainedTagRecipeGeneration; }
    public DeferredClientRefreshTracker deferredClientRefreshTracker() { return deferredClientRefreshTracker; }

    public synchronized LootDataCommitTransaction lootDataTransaction() { return lootDataTransaction; }
    public synchronized ActiveLootDataGeneration retainedLootDataGeneration() { return retainedLootDataGeneration; }
    public synchronized ActiveLootDataGeneration activeLootDataGeneration() { return activeLootDataGeneration; }

    public synchronized LootDataCommitTransaction requestLootDataCommit(MinecraftServer server, String requester) {
        if (stateMachine.state() == PartialReloadState.DEGRADED) {
            throw new IllegalStateException("LOOT_DATA_TRANSACTION_DEGRADED: restart is required");
        }
        if (lootDataTransaction != null && !lootTransactionTerminal(lootDataTransaction.status())) {
            throw new IllegalStateException("LOOT_COMMIT_TRANSACTION_RUNNING");
        }
        if (stateMachine.state() != PartialReloadState.READY) {
            throw new IllegalStateException("LOOT_COMMIT_ARTIFACT_INVALID: state must be READY");
        }
        if (hasGlobalLootModifierChanges()) {
            throw new IllegalStateException("LOOT_COMMIT_REQUIRES_JOINT_GLM_TRANSACTION");
        }
        if (!(preparedArtifact instanceof PreparedLootData artifact) || !artifact.isApplicable()) {
            throw new IllegalStateException("LOOT_COMMIT_ARTIFACT_INVALID");
        }
        if (artifact.expandedCategories().size() != 3
                || !artifact.expandedCategories().equals(PreparedLootData.COMPLETE_SCOPE)) {
            throw new IllegalStateException("LOOT_COMMIT_CANDIDATE_INCOMPLETE");
        }
        var manager = server.getLootData();
        ActiveLootDataGeneration active = LootDataManagerBridge.capture(manager);
        ActiveLootDataGeneration candidate = LootDataManagerBridge.fromPrepared(artifact);
        LootDataManagerBridge.validateComplete(candidate.elements(), candidate.keysByType());
        if (!currentLootResourcesMatchReal(server, artifact.sourceSnapshot())) {
            throw new IllegalStateException("LOOT_COMMIT_SNAPSHOT_STALE");
        }
        LootDataCommitTransaction tx = new LootDataCommitTransaction(UUID.randomUUID(), artifact.preparationId(),
                Instant.now(), requester, System.identityHashCode(manager), active.compatibilityFingerprint(), active);
        tx.candidateGeneration(candidate);
        tx.status(LootDataTransactionStatus.READY);
        lootDataTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    public synchronized LootDataCommitTransaction requestLootDataRollback(MinecraftServer server, String requester) {
        if (stateMachine.state() == PartialReloadState.DEGRADED) {
            throw new IllegalStateException("LOOT_DATA_TRANSACTION_DEGRADED: restart is required");
        }
        if (retainedLootDataGeneration == null) {
            throw new IllegalStateException("LOOT_DATA_MANUAL_ROLLBACK_UNAVAILABLE");
        }
        if (stateMachine.state() != PartialReloadState.SUCCESS && stateMachine.state() != PartialReloadState.IDLE) {
            throw new IllegalStateException("LOOT_COMMIT_TRANSACTION_RUNNING");
        }
        var manager = server.getLootData();
        ActiveLootDataGeneration active = LootDataManagerBridge.capture(manager);
        LootDataCommitTransaction tx = new LootDataCommitTransaction(UUID.randomUUID(), null, Instant.now(), requester,
                System.identityHashCode(manager), active.compatibilityFingerprint(), active);
        tx.previousGeneration(retainedLootDataGeneration);
        tx.candidateGeneration(retainedLootDataGeneration);
        tx.status(LootDataTransactionStatus.READY);
        lootDataTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    public synchronized void processLootDataSafePoint(MinecraftServer server) {
        LootDataCommitTransaction tx = lootDataTransaction;
        if (tx == null || tx.status() != LootDataTransactionStatus.READY) return;
        if (!server.isSameThread()) {
            failLootBeforeMutation(tx, "LOOT_COMMIT_WRONG_THREAD");
            return;
        }
        var manager = server.getLootData();
        try {
            preflightLootDataSafePoint(server, manager, tx);
            ActiveLootDataGeneration previous = LootDataManagerBridge.capture(manager);
            tx.previousGeneration(previous);
            if (tx.preparationId() != null) {
                retainedLootDataSourceSnapshot = activeReference;
            }
            retainedLootDataGeneration = previous;
            stateMachine.transitionTo(PartialReloadState.COMMITTING);
            tx.status(LootDataTransactionStatus.COMMITTING);
            LootDataFaultInjection.hit(LootDataFaultPoint.BEFORE_PUBLICATION);
            LootDataManagerBridge.publishElements(manager, tx.candidateGeneration());
            tx.elementsPublished(true);
            LootDataFaultInjection.hit(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION);
            LootDataManagerBridge.publishTypeIndex(manager, tx.candidateGeneration());
            tx.typeIndexPublished(true);
            LootDataFaultInjection.hit(LootDataFaultPoint.AFTER_TYPE_INDEX_PUBLICATION);
            tx.status(LootDataTransactionStatus.VERIFYING);
            stateMachine.transitionTo(PartialReloadState.VERIFYING);
            LootDataFaultInjection.hit(LootDataFaultPoint.DURING_VERIFICATION);
            LootDataManagerBridge.verify(manager, tx.candidateGeneration());
            tx.verificationPassed(true);
            tx.status(LootDataTransactionStatus.SUCCESS);
            activeLootDataGeneration = tx.candidateGeneration();
            if (tx.preparationId() == null) {
                retainedLootDataGeneration = null;
                if (retainedLootDataSourceSnapshot != null) {
                    activeReference = retainedLootDataSourceSnapshot;
                    retainedLootDataSourceSnapshot = null;
                }
                tx.status(LootDataTransactionStatus.ROLLED_BACK);
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
            } else {
                PreparedLootData artifact = (PreparedLootData) preparedArtifact;
                activeReference = artifact.sourceSnapshot();
                if (latestScan != null) lastChangeSet = ChangeDetector.diff(activeReference, latestScan);
                preparedArtifact = null;
                stateMachine.transitionTo(PartialReloadState.SUCCESS);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.info(
                        "LOOT_DATA_COMMIT_SUCCESS generation={} predicates={} itemModifiers={} lootTables={} playersConnected={}",
                        activeLootDataGeneration.generationId(), activeLootDataGeneration.predicateCount(),
                        activeLootDataGeneration.itemModifierCount(), activeLootDataGeneration.lootTableCount(),
                        connectedPlayerProbe.playerCount(server));
            }
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            tx.failure(message);
            if (!tx.elementsPublished() && !tx.typeIndexPublished()) {
                failLootBeforeMutation(tx, message);
                return;
            }
            try {
                tx.status(LootDataTransactionStatus.ROLLING_BACK);
                LootDataFaultInjection.hit(LootDataFaultPoint.DURING_ROLLBACK);
                LootDataManagerBridge.publish(manager, tx.previousGeneration());
                LootDataManagerBridge.verify(manager, tx.previousGeneration());
                tx.rollbackPerformed(true);
                tx.verificationPassed(true);
                tx.status(LootDataTransactionStatus.ROLLED_BACK);
                activeLootDataGeneration = tx.previousGeneration();
                retainedLootDataGeneration = null;
                retainedLootDataSourceSnapshot = null;
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.error(
                        "LOOT_DATA_COMMIT_FAILED_ROLLED_BACK transaction={} failure={}",
                        tx.transactionId(), message);
            } catch (RuntimeException rollbackFailure) {
                tx.failure(message + "; LOOT_TRANSACTION_DEGRADED: "
                        + (rollbackFailure.getMessage() == null ? rollbackFailure.getClass().getSimpleName()
                        : rollbackFailure.getMessage()));
                tx.status(LootDataTransactionStatus.DEGRADED);
                lastError = tx.failure();
                stateMachine.transitionTo(PartialReloadState.DEGRADED);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.error(
                        "LOOT_DATA_TRANSACTION_DEGRADED transaction={} failure={}", tx.transactionId(), tx.failure());
            }
        }
    }

    private void preflightLootDataSafePoint(MinecraftServer server,
                                              net.minecraft.world.level.storage.loot.LootDataManager manager,
                                              LootDataCommitTransaction tx) {
        if (stateMachine.state() != PartialReloadState.QUIESCING || lootDataTransaction != tx) {
            throw new IllegalStateException("LOOT_COMMIT_TRANSACTION_RUNNING");
        }
        if (System.identityHashCode(manager) != tx.managerIdentity()) {
            throw new IllegalStateException("LOOT_COMMIT_MANAGER_CHANGED");
        }
        LootDataManagerBridge.validateLayout(manager);
        if (!LootDataManagerBridge.matchesExactly(manager, tx.expectedActiveGeneration())) {
            throw new IllegalStateException("LOOT_COMMIT_MANAGER_CHANGED: active generation");
        }
        if (tx.preparationId() != null) {
            if (!(preparedArtifact instanceof PreparedLootData artifact)
                    || !artifact.preparationId().equals(tx.preparationId()) || !artifact.isApplicable()) {
                throw new IllegalStateException("LOOT_COMMIT_ARTIFACT_INVALID");
            }
            if (!currentLootResourcesMatchReal(server, artifact.sourceSnapshot())) {
                throw new IllegalStateException("LOOT_COMMIT_SNAPSHOT_STALE");
            }
        }
        LootDataManagerBridge.validateComplete(tx.candidateGeneration().elements(),
                tx.candidateGeneration().keysByType());
    }

    private void failLootBeforeMutation(LootDataCommitTransaction tx, String message) {
        tx.failure(message);
        tx.status(LootDataTransactionStatus.FAILED);
        lastError = message;
        stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
    }

    private static boolean lootTransactionTerminal(LootDataTransactionStatus status) {
        return status == LootDataTransactionStatus.SUCCESS || status == LootDataTransactionStatus.ROLLED_BACK
                || status == LootDataTransactionStatus.FAILED || status == LootDataTransactionStatus.DEGRADED;
    }

    public synchronized GlobalLootModifierCommitTransaction globalLootModifierTransaction() {
        return globalLootModifierTransaction;
    }
    public synchronized LootAndGlobalModifiersCommitTransaction lootAndGlmTransaction() {
        return lootAndGlmTransaction;
    }
    public synchronized ActiveGlobalLootModifierGeneration retainedGlobalLootModifierGeneration() {
        return retainedGlobalLootModifierGeneration;
    }

    public synchronized GlobalLootModifierCommitTransaction requestGlobalLootModifierCommit(
            MinecraftServer server, String requester) {
        requireMutationAvailable();
        if (hasLootDataChanges()) throw new IllegalStateException("GLM_COMMIT_REQUIRES_JOINT_LOOT_TRANSACTION");
        if (!(preparedArtifact instanceof PreparedGlobalLootModifiers artifact) || !artifact.isApplicable()) {
            throw new IllegalStateException("GLM_COMMIT_ARTIFACT_INVALID");
        }
        var manager = LootModifierManagerBridge.activeManager();
        ActiveGlobalLootModifierGeneration active = LootModifierManagerBridge.capture(manager);
        GlobalLootModifierCommitTransaction tx = new GlobalLootModifierCommitTransaction(UUID.randomUUID(),
                artifact.preparationId(), Instant.now(), requester, System.identityHashCode(manager), active);
        tx.candidateGeneration(LootModifierManagerBridge.fromPrepared(artifact));
        tx.status(GlobalLootModifierTransactionStatus.READY);
        globalLootModifierTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    public synchronized GlobalLootModifierCommitTransaction requestGlobalLootModifierRollback(
            MinecraftServer server, String requester) {
        requireMutationAvailable();
        if (retainedGlobalLootModifierGeneration == null) {
            throw new IllegalStateException("GLM_MANUAL_ROLLBACK_UNAVAILABLE");
        }
        var manager = LootModifierManagerBridge.activeManager();
        ActiveGlobalLootModifierGeneration active = LootModifierManagerBridge.capture(manager);
        GlobalLootModifierCommitTransaction tx = new GlobalLootModifierCommitTransaction(UUID.randomUUID(), null,
                Instant.now(), requester, System.identityHashCode(manager), active);
        tx.candidateGeneration(retainedGlobalLootModifierGeneration);
        tx.previousGeneration(retainedGlobalLootModifierGeneration);
        tx.status(GlobalLootModifierTransactionStatus.READY);
        globalLootModifierTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    public synchronized LootAndGlobalModifiersCommitTransaction requestLootAndGlmCommit(
            MinecraftServer server, String requester) {
        requireMutationAvailable();
        if (!(preparedArtifact instanceof PreparedLootAndGlobalModifiers artifact) || !artifact.isApplicable()) {
            throw new IllegalStateException("LOOT_GLM_COMMIT_ARTIFACT_INVALID");
        }
        var lootManager = server.getLootData();
        var glmManager = LootModifierManagerBridge.activeManager();
        ActiveLootDataGeneration activeLoot = LootDataManagerBridge.capture(lootManager);
        ActiveGlobalLootModifierGeneration activeGlm = LootModifierManagerBridge.capture(glmManager);
        LootAndGlobalModifiersCommitTransaction tx = new LootAndGlobalModifiersCommitTransaction(UUID.randomUUID(),
                artifact.preparationId(), Instant.now(), requester, System.identityHashCode(lootManager),
                System.identityHashCode(glmManager), activeLoot, activeGlm);
        tx.candidateLootGeneration(LootDataManagerBridge.fromPrepared(artifact.lootData()));
        tx.candidateGlmGeneration(LootModifierManagerBridge.fromPrepared(artifact.globalLootModifiers()));
        tx.status(GlobalLootModifierTransactionStatus.READY);
        lootAndGlmTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    public synchronized LootAndGlobalModifiersCommitTransaction requestLootAndGlmRollback(
            MinecraftServer server, String requester) {
        requireMutationAvailable();
        if (retainedJointLootGeneration == null || retainedJointGlmGeneration == null) {
            throw new IllegalStateException("LOOT_GLM_MANUAL_ROLLBACK_UNAVAILABLE");
        }
        var lootManager = server.getLootData();
        var glmManager = LootModifierManagerBridge.activeManager();
        var activeLoot = LootDataManagerBridge.capture(lootManager);
        var activeGlm = LootModifierManagerBridge.capture(glmManager);
        LootAndGlobalModifiersCommitTransaction tx = new LootAndGlobalModifiersCommitTransaction(UUID.randomUUID(),
                null, Instant.now(), requester, System.identityHashCode(lootManager),
                System.identityHashCode(glmManager), activeLoot, activeGlm);
        tx.candidateLootGeneration(retainedJointLootGeneration);
        tx.candidateGlmGeneration(retainedJointGlmGeneration);
        tx.status(GlobalLootModifierTransactionStatus.READY);
        lootAndGlmTransaction = tx;
        stateMachine.transitionTo(PartialReloadState.QUIESCING);
        return tx;
    }

    private void requireMutationAvailable() {
        if (stateMachine.state() == PartialReloadState.DEGRADED) {
            throw new IllegalStateException("LOOT_GLM_TRANSACTION_DEGRADED");
        }
        if (stateMachine.state() != PartialReloadState.READY
                && stateMachine.state() != PartialReloadState.SUCCESS
                && stateMachine.state() != PartialReloadState.IDLE) {
            throw new IllegalStateException("GLM_COMMIT_TRANSACTION_RUNNING");
        }
    }

    public synchronized void processGlobalLootModifierSafePoint(MinecraftServer server) {
        GlobalLootModifierCommitTransaction tx = globalLootModifierTransaction;
        if (tx == null || tx.status() != GlobalLootModifierTransactionStatus.READY) return;
        if (!server.isSameThread()) { failGlmBeforeMutation(tx, "GLM_COMMIT_WRONG_THREAD"); return; }
        var manager = LootModifierManagerBridge.activeManager();
        try {
            if (System.identityHashCode(manager) != tx.managerIdentity()
                    || !LootModifierManagerBridge.matchesExactly(manager, tx.expectedActiveGeneration())) {
                throw new IllegalStateException("GLM_COMMIT_MANAGER_CHANGED");
            }
            if (tx.preparationId() != null) {
                if (!(preparedArtifact instanceof PreparedGlobalLootModifiers artifact)
                        || !artifact.preparationId().equals(tx.preparationId()) || !artifact.isApplicable()) {
                    throw new IllegalStateException("GLM_COMMIT_ARTIFACT_INVALID");
                }
                if (!currentCategoriesMatch(server, artifact.sourceSnapshot(),
                        Set.of(ReloadCategory.GLOBAL_LOOT_MODIFIERS))) {
                    throw new IllegalStateException("GLM_COMMIT_SNAPSHOT_STALE");
                }
            }
            ActiveGlobalLootModifierGeneration previous = LootModifierManagerBridge.capture(manager);
            tx.previousGeneration(previous);
            retainedGlobalLootModifierGeneration = previous;
            tx.status(GlobalLootModifierTransactionStatus.COMMITTING);
            stateMachine.transitionTo(PartialReloadState.COMMITTING);
            GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.BEFORE_GLM_PUBLICATION);
            LootModifierManagerBridge.publish(manager, tx.candidateGeneration());
            tx.published(true);
            GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.AFTER_GLM_PUBLICATION);
            tx.status(GlobalLootModifierTransactionStatus.VERIFYING);
            stateMachine.transitionTo(PartialReloadState.VERIFYING);
            GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.DURING_GLM_VERIFICATION);
            LootModifierManagerBridge.verify(manager, tx.candidateGeneration());
            tx.verificationPassed(true);
            activeGlobalLootModifierGeneration = tx.candidateGeneration();
            if (tx.preparationId() == null) {
                retainedGlobalLootModifierGeneration = null;
                tx.status(GlobalLootModifierTransactionStatus.ROLLED_BACK);
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
            } else {
                activeReference = ((PreparedGlobalLootModifiers) preparedArtifact).sourceSnapshot();
                preparedArtifact = null;
                tx.status(GlobalLootModifierTransactionStatus.SUCCESS);
                stateMachine.transitionTo(PartialReloadState.SUCCESS);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.info(
                        "GLM_COMMIT_SUCCESS generation={} modifiers={} playersConnected={}",
                        activeGlobalLootModifierGeneration.generationId(),
                        activeGlobalLootModifierGeneration.orderedModifiers().size(),
                        connectedPlayerProbe.playerCount(server));
            }
        } catch (RuntimeException failure) {
            String message = rootMessage(failure);
            tx.failure(message);
            if (!tx.published()) { failGlmBeforeMutation(tx, message); return; }
            try {
                tx.status(GlobalLootModifierTransactionStatus.ROLLING_BACK);
                LootModifierManagerBridge.publish(manager, tx.previousGeneration());
                LootModifierManagerBridge.verify(manager, tx.previousGeneration());
                tx.rollbackPerformed(true);
                retainedGlobalLootModifierGeneration = null;
                activeGlobalLootModifierGeneration = tx.previousGeneration();
                tx.status(GlobalLootModifierTransactionStatus.ROLLED_BACK);
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
            } catch (RuntimeException rollbackFailure) {
                tx.failure(message + "; LOOT_GLM_TRANSACTION_DEGRADED: " + rootMessage(rollbackFailure));
                tx.status(GlobalLootModifierTransactionStatus.DEGRADED);
                stateMachine.transitionTo(PartialReloadState.DEGRADED);
            }
        }
    }

    private void failGlmBeforeMutation(GlobalLootModifierCommitTransaction tx, String message) {
        tx.failure(message);
        tx.status(GlobalLootModifierTransactionStatus.FAILED);
        lastError = message;
        stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
    }

    public synchronized void processLootAndGlmSafePoint(MinecraftServer server) {
        LootAndGlobalModifiersCommitTransaction tx = lootAndGlmTransaction;
        if (tx == null || tx.status() != GlobalLootModifierTransactionStatus.READY) return;
        if (!server.isSameThread()) { failJointBeforeMutation(tx, "GLM_COMMIT_WRONG_THREAD"); return; }
        var lootManager = server.getLootData();
        var glmManager = LootModifierManagerBridge.activeManager();
        try {
            if (System.identityHashCode(lootManager) != tx.lootManagerIdentity()
                    || System.identityHashCode(glmManager) != tx.glmManagerIdentity()
                    || !LootDataManagerBridge.matchesExactly(lootManager, tx.expectedLootGeneration())
                    || !LootModifierManagerBridge.matchesExactly(glmManager, tx.expectedGlmGeneration())) {
                throw new IllegalStateException("LOOT_GLM_COMMIT_MANAGER_CHANGED");
            }
            if (tx.preparationId() != null) {
                if (!(preparedArtifact instanceof PreparedLootAndGlobalModifiers artifact)
                        || !artifact.preparationId().equals(tx.preparationId()) || !artifact.isApplicable()) {
                    throw new IllegalStateException("LOOT_GLM_COMMIT_ARTIFACT_INVALID");
                }
                if (!currentCategoriesMatch(server, artifact.sourceSnapshot(), Set.of(
                        ReloadCategory.PREDICATES, ReloadCategory.ITEM_MODIFIERS,
                        ReloadCategory.LOOT, ReloadCategory.GLOBAL_LOOT_MODIFIERS))) {
                    throw new IllegalStateException("LOOT_GLM_COMMIT_SNAPSHOT_STALE");
                }
            }
            tx.previousLootGeneration(LootDataManagerBridge.capture(lootManager));
            tx.previousGlmGeneration(LootModifierManagerBridge.capture(glmManager));
            retainedJointLootGeneration = tx.previousLootGeneration();
            retainedJointGlmGeneration = tx.previousGlmGeneration();
            retainedJointSourceSnapshot = activeReference;
            tx.status(GlobalLootModifierTransactionStatus.COMMITTING);
            stateMachine.transitionTo(PartialReloadState.COMMITTING);
            LootDataManagerBridge.publishElements(lootManager, tx.candidateLootGeneration());
            LootDataManagerBridge.publishTypeIndex(lootManager, tx.candidateLootGeneration());
            tx.lootPublished(true);
            GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.AFTER_LOOT_BEFORE_GLM);
            LootModifierManagerBridge.publish(glmManager, tx.candidateGlmGeneration());
            tx.glmPublished(true);
            tx.status(GlobalLootModifierTransactionStatus.VERIFYING);
            stateMachine.transitionTo(PartialReloadState.VERIFYING);
            GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.DURING_JOINT_VERIFICATION);
            LootDataManagerBridge.verify(lootManager, tx.candidateLootGeneration());
            LootModifierManagerBridge.verify(glmManager, tx.candidateGlmGeneration());
            tx.verificationPassed(true);
            activeLootDataGeneration = tx.candidateLootGeneration();
            activeGlobalLootModifierGeneration = tx.candidateGlmGeneration();
            if (tx.preparationId() == null) {
                retainedJointLootGeneration = null; retainedJointGlmGeneration = null;
                if (retainedJointSourceSnapshot != null) activeReference = retainedJointSourceSnapshot;
                retainedJointSourceSnapshot = null;
                tx.status(GlobalLootModifierTransactionStatus.ROLLED_BACK);
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
            } else {
                activeReference = ((PreparedLootAndGlobalModifiers) preparedArtifact).sourceSnapshot();
                preparedArtifact = null;
                tx.status(GlobalLootModifierTransactionStatus.SUCCESS);
                stateMachine.transitionTo(PartialReloadState.SUCCESS);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.info(
                        "LOOT_GLM_JOINT_COMMIT_SUCCESS lootGeneration={} glmGeneration={} predicates={} itemModifiers={} lootTables={} globalLootModifiers={} playersConnected={}",
                        activeLootDataGeneration.generationId(), activeGlobalLootModifierGeneration.generationId(),
                        activeLootDataGeneration.predicateCount(), activeLootDataGeneration.itemModifierCount(),
                        activeLootDataGeneration.lootTableCount(), activeGlobalLootModifierGeneration.orderedModifiers().size(),
                        connectedPlayerProbe.playerCount(server));
            }
        } catch (RuntimeException failure) {
            String message = rootMessage(failure); tx.failure(message);
            if (!tx.lootPublished() && !tx.glmPublished()) { failJointBeforeMutation(tx, message); return; }
            try {
                tx.status(GlobalLootModifierTransactionStatus.ROLLING_BACK);
                GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_GLM);
                LootModifierManagerBridge.publish(glmManager, tx.previousGlmGeneration());
                LootModifierManagerBridge.verify(glmManager, tx.previousGlmGeneration());
                GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_LOOT);
                LootDataManagerBridge.publish(lootManager, tx.previousLootGeneration());
                LootDataManagerBridge.verify(lootManager, tx.previousLootGeneration());
                activeLootDataGeneration = tx.previousLootGeneration();
                activeGlobalLootModifierGeneration = tx.previousGlmGeneration();
                retainedJointLootGeneration = null; retainedJointGlmGeneration = null; retainedJointSourceSnapshot = null;
                tx.status(GlobalLootModifierTransactionStatus.ROLLED_BACK);
                stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
            } catch (RuntimeException rollbackFailure) {
                tx.failure(message + "; LOOT_GLM_TRANSACTION_DEGRADED: " + rootMessage(rollbackFailure));
                tx.status(GlobalLootModifierTransactionStatus.DEGRADED);
                stateMachine.transitionTo(PartialReloadState.DEGRADED);
            }
        }
    }

    private void failJointBeforeMutation(LootAndGlobalModifiersCommitTransaction tx, String message) {
        tx.failure(message); tx.status(GlobalLootModifierTransactionStatus.FAILED);
        lastError = message; stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
    }

    private boolean currentCategoriesMatch(MinecraftServer server, ResourceSnapshot expected,
                                           Set<ReloadCategory> categories) {
        try {
            ResourceSnapshot current = scanCurrent(server.getResourceManager());
            for (var entry : expected.resources().entrySet()) {
                if (!categories.contains(entry.getValue().category())) continue;
                var now = current.resources().get(entry.getKey());
                if (now == null || !now.fingerprint().hash().equals(entry.getValue().fingerprint().hash())
                        || !now.sourcePack().equals(entry.getValue().sourcePack())) return false;
            }
            for (var entry : current.resources().entrySet()) {
                if (categories.contains(entry.getValue().category()) && !expected.resources().containsKey(entry.getKey())) return false;
            }
            return true;
        } catch (Exception error) {
            lastError = "LOOT_GLM_COMMIT_SNAPSHOT_STALE: " + rootMessage(error);
            return false;
        }
    }

    private boolean currentLootResourcesMatchReal(MinecraftServer server, ResourceSnapshot expected) {
        try {
            ResourceSnapshot current = new ResourceScanner(Clock.systemUTC()).scan(new ScanContext(
                    server.getResourceManager(), PartialReloadConfig.maxScannedResources(),
                    java.time.Duration.ofSeconds(PartialReloadConfig.scanTimeoutSeconds()), true));
            for (var entry : expected.resources().entrySet()) {
                if (!PreparedLootData.COMPLETE_SCOPE.contains(entry.getValue().category())) continue;
                var now = current.resources().get(entry.getKey());
                if (now == null || !now.fingerprint().hash().equals(entry.getValue().fingerprint().hash())
                        || !now.sourcePack().equals(entry.getValue().sourcePack())) return false;
            }
            for (var entry : current.resources().entrySet()) {
                if (PreparedLootData.COMPLETE_SCOPE.contains(entry.getValue().category())
                        && !expected.resources().containsKey(entry.getKey())) return false;
            }
            return true;
        } catch (Exception exception) {
            lastError = "LOOT_COMMIT_SNAPSHOT_STALE: " + exception.getMessage();
            return false;
        }
    }

    public void onPlayerLogin(UUID playerId) {
        deferredClientRefreshTracker.remove(playerId);
    }

    public void onPlayerLogout(UUID playerId) {
        deferredClientRefreshTracker.remove(playerId);
    }

    public void clearDeferredClientRefreshState() {
        deferredClientRefreshTracker.clear();
        deferredPlayerSessionProbe = DeferredPlayerSessionProbe.DEFAULT;
    }

    public List<String> stalePlayerNames(MinecraftServer server, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit");
        Set<UUID> stale = deferredClientRefreshTracker.stalePlayerIds();
        Map<UUID, String> names = new LinkedHashMap<>();
        server.getPlayerList().getPlayers().forEach(player -> names.put(player.getUUID(), player.getGameProfile().getName()));
        TagRecipeCommitTransaction current = tagRecipeTransaction;
        if (current != null) names.putAll(current.deferredPlayerSnapshot());
        return stale.stream().map(id -> names.getOrDefault(id, "<unknown>"))
                .sorted().limit(limit).toList();
    }

    public synchronized TagRecipeCommitTransaction requestTagRecipeCommit(MinecraftServer server, String requester) {
        return requestTagRecipeCommit(server, requester, TagRecipeConnectedPlayerPolicy.REJECT);
    }

    public synchronized TagRecipeCommitTransaction requestTagRecipeCommit(MinecraftServer server, String requester,
                                                                           TagRecipeConnectedPlayerPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (stateMachine.state() == PartialReloadState.DEGRADED) throw new IllegalStateException("TAG_RECIPE_TRANSACTION_DEGRADED: restart is required");
        if (tagRecipeTransaction != null && tagRecipeTransaction.status() != TagRecipeTransactionStatus.SUCCESS && tagRecipeTransaction.status() != TagRecipeTransactionStatus.ROLLED_BACK && tagRecipeTransaction.status() != TagRecipeTransactionStatus.FAILED_SAFE && tagRecipeTransaction.status() != TagRecipeTransactionStatus.DEGRADED)
            throw new IllegalStateException("TAG_RECIPE_COMMIT_TRANSACTION_RUNNING");
        if (stateMachine.state() != PartialReloadState.READY) throw new IllegalStateException("TAG_RECIPE_COMMIT_ARTIFACT_REQUIRED");
        if (!(preparedArtifact instanceof PreparedTagsAndRecipes joint) || !joint.isApplicable()) throw new IllegalStateException("TAG_RECIPE_COMMIT_ARTIFACT_INVALID");
        policy.validateConnectedPlayerCount(connectedPlayerProbe.playerCount(server));
        TagRecipeCommitCompatibility compatibility = TagRecipeCommitCompatibility.inspect(server);
        if (!compatibility.compatible()) throw new IllegalStateException("TAG_RECIPE_COMMIT_NOT_COMPATIBLE: " + compatibility.detail());
        TagRecipeCommitTransaction tx = new TagRecipeCommitTransaction(UUID.randomUUID(), joint.preparationId(), Instant.now(), requester, policy);
        tx.recipeManagerIdentity(compatibility.recipeManagerIdentity());
        tx.registryAccessIdentity(compatibility.registryAccessIdentity());
        tx.compatibilityFingerprint(compatibility.fingerprint());
        tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.PREFLIGHT_PASSED, TagRecipeTransactionStatus.PREFLIGHT, "INITIAL_PREFLIGHT_PASSED"); tx.status(TagRecipeTransactionStatus.PREFLIGHT); tx.status(TagRecipeTransactionStatus.QUIESCING);
        tagRecipeTransaction = tx; stateMachine.transitionTo(PartialReloadState.QUIESCING); return tx;
    }

    public synchronized TagRecipeCommitTransaction requestTagRecipeRollback(String requester) {
        if (stateMachine.state() == PartialReloadState.DEGRADED) throw new IllegalStateException("TAG_RECIPE_TRANSACTION_DEGRADED: restart is required");
        if (retainedTagRecipeGeneration == null) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_UNAVAILABLE");
        if (stateMachine.state() != PartialReloadState.SUCCESS && stateMachine.state() != PartialReloadState.IDLE) throw new IllegalStateException("TAG_RECIPE_COMMIT_TRANSACTION_RUNNING");
        TagRecipeCommitTransaction tx = new TagRecipeCommitTransaction(UUID.randomUUID(), null, Instant.now(), requester);
        tx.previousGeneration(retainedTagRecipeGeneration); tx.registriesToMutate(retainedTagRecipeGeneration.tags().registries().keySet()); for (var key:retainedTagRecipeGeneration.tags().registries().keySet()) tx.tagRegistryMutated(key); tx.status(TagRecipeTransactionStatus.QUIESCING); tagRecipeTransaction=tx; stateMachine.transitionTo(PartialReloadState.QUIESCING); return tx;
    }

    public synchronized TagRecipeCommitTransaction requestTagRecipeRollback(MinecraftServer server, String requester) {
        if (connectedPlayerProbe.playerCount(server) > 0) {
            throw new IllegalStateException("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED");
        }
        return requestTagRecipeRollback(requester);
    }

    public synchronized void processTagRecipeSafePoint(MinecraftServer server) {
        TagRecipeCommitTransaction tx=tagRecipeTransaction; if(tx==null || tx.status()!=TagRecipeTransactionStatus.QUIESCING) return;
        if (tagRecipeSafePointHeld) return;
        try {
            if (tx.preparationId()==null) { stateMachine.transitionTo(PartialReloadState.COMMITTING); restoreTagRecipeGeneration(server, tx.previousGeneration(), tx); return; }
            PreparedTagsAndRecipes artifact=preparedArtifact instanceof PreparedTagsAndRecipes j ? j : null;
            tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.SAFE_POINT_REACHED, TagRecipeTransactionStatus.QUIESCING, "SAFE_POINT_REACHED"); preflightTagRecipeCommit(server, tx, artifact); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.PREFLIGHT_PASSED, TagRecipeTransactionStatus.QUIESCING, "SAFE_POINT_PREFLIGHT_PASSED");
            List<DeferredPlayerSession> deferredSessions = prepareDeferredPlayersAtSafePoint(server, tx);
            Set<ResourceKey<? extends Registry<?>>> registriesToMutate=deriveRegistriesToMutate(artifact);
            tx.registriesToMutate(registriesToMutate); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.REGISTRY_SCOPE_DERIVED, TagRecipeTransactionStatus.BUILDING_BINDINGS, "REGISTRY_SCOPE_DERIVED:" + registriesToMutate);
            Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> candidate=buildCandidateBindings(server.registryAccess(), artifact.preparedTags(), registriesToMutate); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.CANDIDATE_BINDINGS_BUILT, TagRecipeTransactionStatus.BUILDING_BINDINGS, "bindings=" + candidate.size());
            List<Recipe<?>> candidateRecipes = new ArrayList<>();
            for (PreparedRecipe preparedRecipe : artifact.preparedRecipes().recipesById().values()) {
                candidateRecipes.add(preparedRecipe.recipe());
            }
            tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.CANDIDATE_RECIPES_BUILT, TagRecipeTransactionStatus.BUILDING_BINDINGS, "recipes=" + candidateRecipes.size());
            ActiveTagRecipeGeneration previous=captureTagRecipeGeneration(server, registriesToMutate, artifact.preparedTags()); tx.previousGeneration(previous); retainedTagRecipeGeneration=previous; tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.PREVIOUS_GENERATION_CAPTURED, TagRecipeTransactionStatus.READY_TO_COMMIT, previous.generationId().toString());
            stateMachine.transitionTo(PartialReloadState.COMMITTING);
            tx.status(TagRecipeTransactionStatus.BINDING_TAGS);
            int bindIndex=0; for (var e:candidate.entrySet()) { tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAG_REGISTRY_REPLACEMENT_STARTED, TagRecipeTransactionStatus.BINDING_TAGS, e.getKey().location().toString()); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAG_BIND_STARTED, TagRecipeTransactionStatus.BINDING_TAGS, e.getKey().location().toString()); if(bindIndex==0) TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_FIRST_TAG_BIND); if(bindIndex==1) TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_SECOND_TAG_BIND); bind(server.registryAccess(),e.getKey(),e.getValue()); tx.tagRegistryMutated(e.getKey()); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAG_REGISTRY_REPLACED_EXACT, TagRecipeTransactionStatus.BINDING_TAGS, e.getKey().location().toString()); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAG_BOUND, TagRecipeTransactionStatus.BINDING_TAGS, e.getKey().location().toString()); if(bindIndex==0) TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_FIRST_TAG_BIND); bindIndex++; }
            TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_ALL_TAG_BINDS);
            tx.status(TagRecipeTransactionStatus.PUBLISHING_RECIPES);
            tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.RECIPE_PUBLICATION_STARTED, TagRecipeTransactionStatus.PUBLISHING_RECIPES, "count=" + candidateRecipes.size()); TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_RECIPE_PUBLICATION); server.getRecipeManager().replaceRecipes(candidateRecipes); tx.recipeMutationOccurred(true); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.RECIPES_PUBLISHED, TagRecipeTransactionStatus.PUBLISHING_RECIPES, "count=" + candidateRecipes.size()); TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION);
            tx.status(TagRecipeTransactionStatus.INVALIDATING_CACHES); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.INGREDIENT_INVALIDATION_STARTED, TagRecipeTransactionStatus.INVALIDATING_CACHES, "start"); Ingredient.invalidateAll(); tx.ingredientInvalidationOccurred(true); tx.ingredientCommitInvalidations(tx.ingredientCommitInvalidations()+1); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.INGREDIENTS_INVALIDATED, TagRecipeTransactionStatus.INVALIDATING_CACHES, "count=1"); TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_INGREDIENT_INVALIDATION);
            tx.status(TagRecipeTransactionStatus.DISPATCHING_EVENTS); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAGS_EVENT_DISPATCH_STARTED, TagRecipeTransactionStatus.DISPATCHING_EVENTS, "start"); if (MinecraftForge.EVENT_BUS.post(new TagsUpdatedEvent(server.registryAccess(), false, false))) throw new IllegalStateException("TAG_UPDATE_EVENT_FAILED"); tx.tagsUpdatedEventDispatched(true); tx.commitTagEvents(tx.commitTagEvents()+1); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.TAGS_EVENT_DISPATCHED, TagRecipeTransactionStatus.DISPATCHING_EVENTS, "count=1"); TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_TAGS_UPDATED_EVENT);
            tx.status(TagRecipeTransactionStatus.VERIFYING_SERVER); stateMachine.transitionTo(PartialReloadState.VERIFYING); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.VERIFICATION_STARTED, TagRecipeTransactionStatus.VERIFYING_SERVER, "start"); TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_VERIFICATION); verifyTagRecipe(server, candidate, artifact, tx);
            tx.candidateGeneration(new ActiveTagRecipeGeneration(UUID.randomUUID(),Instant.now(),new ActiveTagGeneration(UUID.randomUUID(),Instant.now(),captureTags(server.registryAccess(),tx.registriesToMutate()), server.registryAccess()),new ActiveRecipeGeneration(UUID.randomUUID(),Instant.now(),server.getRecipeManager().getRecipes()),artifact.sourceSnapshot())); tx.verificationPassed(true); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.VERIFICATION_PASSED, TagRecipeTransactionStatus.VERIFYING_SERVER, "passed"); tx.status(TagRecipeTransactionStatus.SUCCESS); activeTagRecipeGeneration=tx.candidateGeneration(); promoteTagRecipeBaseline(artifact, tx.registriesToMutate()); preparedArtifact=null; stateMachine.transitionTo(PartialReloadState.SUCCESS);
            long generation = deferredClientRefreshTracker.confirmCommit(tx.connectedPlayerPolicy(), tx.deferredPlayerSnapshot().keySet());
            tx.deferredClientRefreshGeneration(generation);
            if (tx.connectedPlayerPolicy() == TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN) {
                tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.DEFERRED_CLIENTS_MARKED_STALE,
                        TagRecipeTransactionStatus.SUCCESS, "generation=" + generation + ",players=" + tx.deferredPlayerSnapshot().size());
                notifyDeferredPlayers(deferredSessions);
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.info(
                        "TAG_RECIPE_COMMIT_SUCCESS_DEFERRED_CLIENT_REFRESH generation={} connectedPlayers={} stalePlayers={}",
                        generation, tx.deferredPlayerSnapshot().size(), deferredClientRefreshTracker.staleCount());
            }
        } catch (RuntimeException failure) {
            lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            tx.failure(lastError);
            tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.FAILURE,
                    TagRecipeTransactionStatus.FAILED_SAFE, lastError);
            if (!tx.tagMutationOccurred() && !tx.recipeMutationOccurred() && !tx.ingredientInvalidationOccurred() && !tx.tagsUpdatedEventDispatched()) {
                tx.status(TagRecipeTransactionStatus.FAILED_SAFE);
                stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
                return;
            }
            tx.status(TagRecipeTransactionStatus.ROLLBACK_REQUESTED);
            try { TagRecipeFaultInjection.hit(TagRecipeFaultPoint.DURING_ROLLBACK); restoreTagRecipeGeneration(server,tx.previousGeneration(),tx); }
            catch(RuntimeException rollback){
                lastError = "TAG_RECIPE_ROLLBACK_FAILED: " + (rollback.getMessage() == null ? rollback.getClass().getSimpleName() : rollback.getMessage());
                tx.failure(lastError);
                tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.DEGRADED, TagRecipeTransactionStatus.DEGRADED, lastError); tx.restartRequired(true); tx.status(TagRecipeTransactionStatus.DEGRADED); stateMachine.transitionTo(PartialReloadState.DEGRADED);
            }
        }
    }

    private static void bind(RegistryAccess access, ResourceKey<? extends Registry<?>> key, Map<TagKey<?>, List<Holder<?>>> map) {
        com.gabriel0liv.partialreload.joint.MappedRegistryTagBridge.replaceExact(access, key, map);
    }
    private void preflightTagRecipeCommit(MinecraftServer server, TagRecipeCommitTransaction tx, PreparedTagsAndRecipes artifact) {
        if (stateMachine.state()!=PartialReloadState.QUIESCING || tagRecipeTransaction!=tx) throw new IllegalStateException("TAG_RECIPE_COMMIT_STATE_CHANGED");
        if (artifact==null || preparedArtifact!=artifact || !artifact.preparationId().equals(tx.preparationId()) || !artifact.isApplicable()) throw new IllegalStateException("TAG_RECIPE_COMMIT_SNAPSHOT_STALE");
        tx.connectedPlayerPolicy().validateConnectedPlayerCount(connectedPlayerProbe.playerCount(server));
        if (System.identityHashCode(server.getRecipeManager())!=tx.recipeManagerIdentity() || System.identityHashCode(server.registryAccess())!=tx.registryAccessIdentity()) throw new IllegalStateException("TAG_RECIPE_COMMIT_STATE_CHANGED");
        TagRecipeCommitCompatibility compatibility=TagRecipeCommitCompatibility.inspect(server);
        if (!compatibility.compatible() || !compatibility.fingerprint().equals(tx.compatibilityFingerprint())) throw new IllegalStateException("TAG_RECIPE_COMMIT_NOT_COMPATIBLE");
            if (!currentResourceProbe.matches(server, artifact.sourceSnapshot())) throw new IllegalStateException("TAG_RECIPE_COMMIT_SNAPSHOT_STALE");
        deriveRegistriesToMutate(artifact); // validates unsupported changes before mutation
    }

    private List<DeferredPlayerSession> prepareDeferredPlayersAtSafePoint(MinecraftServer server,
                                                                           TagRecipeCommitTransaction tx) {
        if (tx.connectedPlayerPolicy() != TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN) {
            return List.of();
        }
        List<DeferredPlayerSession> sessions = List.copyOf(deferredPlayerSessionProbe.capture(server));
        Map<UUID, String> snapshot = new LinkedHashMap<>();
        for (DeferredPlayerSession session : sessions) {
            if (snapshot.put(session.playerId(), session.playerName()) != null) {
                throw new IllegalStateException("TAG_RECIPE_DEFERRED_PLAYER_SNAPSHOT_INVALID");
            }
        }
        tx.deferredPlayerSnapshot(snapshot);
        tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.DEFERRED_PLAYERS_CAPTURED,
                TagRecipeTransactionStatus.QUIESCING, "players=" + sessions.size());
        tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.DEFERRED_MENU_CLOSE_STARTED,
                TagRecipeTransactionStatus.QUIESCING, "players=" + sessions.size());
        for (DeferredPlayerSession session : sessions) {
            session.closeContainer().run();
        }
        List<String> failed = sessions.stream().filter(session -> !session.inventoryMenuActive().getAsBoolean())
                .map(DeferredPlayerSession::playerName).sorted().toList();
        if (!failed.isEmpty()) {
            throw new IllegalStateException("TAG_RECIPE_DEFERRED_MENU_CLOSE_FAILED: " + failed);
        }
        tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.DEFERRED_MENUS_CLOSED,
                TagRecipeTransactionStatus.QUIESCING, "players=" + sessions.size());
        return sessions;
    }

    private static void notifyDeferredPlayers(List<DeferredPlayerSession> sessions) {
        Component message = Component.literal("[Partial Reload] As receitas e tags do servidor foram atualizadas. "
                + "Relogue para atualizar o recipe book, JEI/REI e outras informações visuais. "
                + "A lógica do servidor já está usando os novos dados.").withStyle(ChatFormatting.GOLD);
        for (DeferredPlayerSession session : sessions) {
            try {
                session.messageSink().accept(message);
            } catch (RuntimeException notificationFailure) {
                com.gabriel0liv.partialreload.PartialReloadMod.LOGGER.warn(
                        "Deferred refresh warning could not be delivered to {}", session.playerName(), notificationFailure);
            }
        }
    }

    private boolean currentResourcesMatchReal(MinecraftServer server, ResourceSnapshot expected) {
        try {
            ResourceSnapshot current=new ResourceScanner(Clock.systemUTC()).scan(new ScanContext(server.getResourceManager(), PartialReloadConfig.maxScannedResources(), java.time.Duration.ofSeconds(PartialReloadConfig.scanTimeoutSeconds()), true));
            for (var entry:expected.resources().entrySet()) if ((entry.getValue().category()==ReloadCategory.TAGS || entry.getValue().category()==ReloadCategory.RECIPES)) { var now=current.resources().get(entry.getKey()); if(now==null || !now.fingerprint().hash().equals(entry.getValue().fingerprint().hash()) || !now.sourcePack().equals(entry.getValue().sourcePack())) return false; }
            for (var entry:current.resources().entrySet()) if ((entry.getValue().category()==ReloadCategory.TAGS || entry.getValue().category()==ReloadCategory.RECIPES) && !expected.resources().containsKey(entry.getKey())) return false;
            return true;
        } catch (Exception ex) { lastError="TAG_RECIPE_COMMIT_SNAPSHOT_STALE: "+ex.getMessage(); return false; }
    }

    private Set<ResourceKey<? extends Registry<?>>> deriveRegistriesToMutate(PreparedTagsAndRecipes artifact) {
        Set<ResourceKey<? extends Registry<?>>> result=new LinkedHashSet<>();
        if (activeReference==null) return Set.of();
        Set<ResourceLocation> resources=new LinkedHashSet<>(activeReference.resources().keySet()); resources.addAll(artifact.sourceSnapshot().resources().keySet());
        for (ResourceLocation resource:resources) {
            var before=activeReference.resources().get(resource); var after=artifact.sourceSnapshot().resources().get(resource);
            if ((before!=null && before.category()!=ReloadCategory.TAGS) || (after!=null && after.category()!=ReloadCategory.TAGS)) continue;
            if (before!=null && after!=null && before.fingerprint().hash().equals(after.fingerprint().hash()) && before.sourcePack().equals(after.sourcePack())) continue;
            String path=resource.getPath(); if(!path.startsWith("tags/")) continue; String rest=path.substring(5); int slash=rest.indexOf('/'); if(slash<0) continue; String registry=rest.substring(0,slash);
            if (registry.equals("worldgen")) { String tail=rest.substring(slash+1); int second=tail.indexOf('/'); if(second<0) continue; registry="worldgen/"+tail.substring(0,second); }
            String canonical=canonicalRegistry(registry); if(!com.gabriel0liv.partialreload.joint.TagRegistryMutationScopeResolver.supported(registry, com.gabriel0liv.partialreload.joint.TagRegistryMutationScopeResolver.Operation.MODIFY, resource.getNamespace())) throw new IllegalStateException(com.gabriel0liv.partialreload.joint.TagRegistryMutationScopeResolver.blocker(registry, com.gabriel0liv.partialreload.joint.TagRegistryMutationScopeResolver.Operation.MODIFY, resource.getNamespace())); result.add(registryKey(canonical));
        }
        return result.stream().sorted(java.util.Comparator.comparing(k -> k.location().toString())).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked") private static ResourceKey<Registry<Object>> registryKey(String canonical){return (ResourceKey<Registry<Object>>)(ResourceKey<?>)ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft",canonical));}

    @SuppressWarnings("unchecked") private static Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> buildCandidateBindings(RegistryAccess access, PreparedTags prepared, Set<ResourceKey<? extends Registry<?>>> scope) {
        Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> out=new LinkedHashMap<>();
        for (var entry:prepared.registries().entrySet()) { String path=entry.getKey(); String canonical=canonicalRegistry(path); if (canonical==null) continue; ResourceKey<Registry<Object>> key=registryKey(canonical); if(!scope.contains(key)) continue; Registry<Object> registry=access.registryOrThrow(key); Map<TagKey<?>,List<Holder<?>>> tags=new LinkedHashMap<>();
            registry.getTags().forEach(pair -> {
                TagKey<Object> tagKey = pair.getFirst();
                HolderSet.Named<Object> named = pair.getSecond();
                List<Holder<?>> holders = new ArrayList<>();
                named.forEach(holders::add);
                tags.put(tagKey, List.copyOf(holders));
            });
            for (var tagEntry:entry.getValue().tags().entrySet()) { List<Holder<?>> holders=new ArrayList<>(); for(String value:expandTag(entry.getValue().tags(),tagEntry.getKey(),new HashSet<>())) { ResourceKey<Object> memberKey=ResourceKey.create(key,ResourceLocation.parse(value)); Holder<Object> holder=registry.getHolder(memberKey).orElseThrow(()->new IllegalStateException("TAG_COMMIT_MEMBER_MISSING: "+value)); holders.add(holder); } tags.put(TagKey.create(key,tagEntry.getKey()),holders); } out.put(key,tags); }
        for (var key:scope) { out.putIfAbsent(key, new LinkedHashMap<>()); out.get(key).entrySet().removeIf(e -> prepared.delta().tagsRemoved().contains(e.getKey().location())); }
        return out;
    }
    private static Set<String> expandTag(Map<ResourceLocation, com.gabriel0liv.partialreload.tags.PreparedTag> tags, ResourceLocation id, Set<ResourceLocation> visiting){ if(!visiting.add(id)) throw new IllegalStateException("TAG_COMMIT_BINDING_BUILD_FAILED: cycle"); var tag=tags.get(id); if(tag==null) throw new IllegalStateException("TAG_COMMIT_BINDING_BUILD_FAILED: missing nested tag"); Set<String> out=new LinkedHashSet<>(); for(String v:tag.orderedEntries()){if(v.startsWith("#")) out.addAll(expandTag(tags,ResourceLocation.parse(v.substring(1)),visiting)); else if(!tag.missingOptionalEntries().contains(v)) out.add(v);} visiting.remove(id); return out; }
    private static String canonicalRegistry(String path){return switch(path){case "items"->"item";case "blocks"->"block";case "fluids"->"fluid";case "entity_types"->"entity_type";case "game_events"->"game_event";case "mob_effects"->"mob_effect";case "enchantments"->"enchantment";default->null;};}
    private static Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> captureTags(RegistryAccess access, Set<ResourceKey<? extends Registry<?>>> scope){Map<ResourceKey<? extends Registry<?>>,Map<TagKey<?>,List<Holder<?>>>> out=new LinkedHashMap<>(); for (ResourceKey<? extends Registry<?>> key:scope.stream().sorted(java.util.Comparator.comparing(k -> k.location().toString())).toList()) { Registry<?> registry=access.registryOrThrow(key); Map<TagKey<?>,List<Holder<?>>> tags=new LinkedHashMap<>(); registry.getTags().forEach(pair->{ List<Holder<?>> holders=new ArrayList<>(); pair.getSecond().forEach(holders::add); tags.put(pair.getFirst(),List.copyOf(holders));}); out.put(key,tags); } return out;}
    private ActiveTagRecipeGeneration captureTagRecipeGeneration(MinecraftServer server, Set<ResourceKey<? extends Registry<?>>> scope, PreparedTags candidate){
        Map<ResourceKey<? extends Registry<?>>, Set<ResourceLocation>> universe = new LinkedHashMap<>();
        for (var entry : candidate.registries().entrySet()) { String canonical = canonicalRegistry(entry.getKey()); if (canonical == null) continue; ResourceKey<Registry<Object>> key = registryKey(canonical); universe.put(key, entry.getValue().tags().keySet()); }
        return new ActiveTagRecipeGeneration(UUID.randomUUID(),Instant.now(),new ActiveTagGeneration(UUID.randomUUID(),Instant.now(),captureTags(server.registryAccess(),scope), server.registryAccess(), universe),new ActiveRecipeGeneration(UUID.randomUUID(),Instant.now(),server.getRecipeManager().getRecipes()),activeReference==null?latestScan:activeReference);
    }
    private void restoreTagRecipeGeneration(MinecraftServer server, ActiveTagRecipeGeneration generation, TagRecipeCommitTransaction tx){
        if(generation==null) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_FAILED");
        tx.status(TagRecipeTransactionStatus.ROLLING_BACK_RECIPES); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_STARTED, TagRecipeTransactionStatus.ROLLING_BACK_RECIPES,"start"); if (tx.preparationId()==null || tx.recipePublicationOccurred()) { server.getRecipeManager().replaceRecipes(generation.recipes().recipes()); tx.recipeMutationOccurred(true); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_RECIPES_RESTORED, TagRecipeTransactionStatus.ROLLING_BACK_RECIPES,"restored"); }
        tx.status(TagRecipeTransactionStatus.ROLLING_BACK_TAGS); for(var e:generation.tags().registries().entrySet()) if(tx.mutatedTagRegistries().contains(e.getKey())) { tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_TAG_REGISTRY_REPLACEMENT_STARTED, TagRecipeTransactionStatus.ROLLING_BACK_TAGS,e.getKey().location().toString()); bind(server.registryAccess(),e.getKey(),e.getValue()); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_TAG_RESTORED, TagRecipeTransactionStatus.ROLLING_BACK_TAGS,e.getKey().location().toString()); }
        if (!tx.mutatedTagRegistries().isEmpty() || tx.preparationId()==null || tx.recipePublicationOccurred()) { Ingredient.invalidateAll(); tx.ingredientInvalidationOccurred(true); tx.ingredientRollbackInvalidations(tx.ingredientRollbackInvalidations()+1); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_INGREDIENT_INVALIDATION_STARTED, TagRecipeTransactionStatus.ROLLBACK_EVENTS,"start"); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_INGREDIENTS_INVALIDATED, TagRecipeTransactionStatus.ROLLBACK_EVENTS,"count=1"); tx.status(TagRecipeTransactionStatus.ROLLBACK_EVENTS); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCH_STARTED, TagRecipeTransactionStatus.ROLLBACK_EVENTS,"start"); MinecraftForge.EVENT_BUS.post(new TagsUpdatedEvent(server.registryAccess(),false,false)); tx.tagsUpdatedEventDispatched(true); tx.rollbackTagEvents(tx.rollbackTagEvents()+1); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_TAGS_EVENT_DISPATCHED, TagRecipeTransactionStatus.ROLLBACK_EVENTS,"count=1"); }
        tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_STARTED, TagRecipeTransactionStatus.VERIFYING_SERVER,"start");
        TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_ROLLBACK_VERIFICATION);
        verifyRestoredTagRecipe(server, generation, tx); tx.verificationPassed(true); tx.event(com.gabriel0liv.partialreload.joint.TagRecipeTransactionEventType.ROLLBACK_VERIFICATION_PASSED, TagRecipeTransactionStatus.VERIFYING_SERVER,"ROLLBACK_VERIFICATION_PASSED"); tx.status(TagRecipeTransactionStatus.ROLLED_BACK); activeTagRecipeGeneration=generation; retainedTagRecipeGeneration=null;
        if (generation.sourceSnapshot()!=null) { activeReference=generation.sourceSnapshot(); if (latestScan!=null) lastChangeSet=ChangeDetector.diff(activeReference, latestScan); }
        stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
    }
    private void verifyTagRecipe(MinecraftServer server, Map<ResourceKey<? extends Registry<?>>,Map<TagKey<?>,List<Holder<?>>>> candidate, PreparedTagsAndRecipes artifact, TagRecipeCommitTransaction tx){
        tx.connectedPlayerPolicy().validateConnectedPlayerCount(connectedPlayerProbe.playerCount(server));
        Set<ResourceLocation> expected=artifact.preparedRecipes().recipesById().keySet(); Set<ResourceLocation> actual=server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(java.util.stream.Collectors.toSet());
        if(!actual.equals(expected))throw new IllegalStateException("RECIPE_COMMIT_VERIFICATION_FAILED");
        for (var entry : candidate.entrySet()) { Registry<?> registry=server.registryAccess().registryOrThrow(entry.getKey()); Map<TagKey<?>, List<Holder<?>>> expectedTags=entry.getValue(); Map<TagKey<?>, List<Holder<?>>> observedTags=new LinkedHashMap<>(); registry.getTags().forEach(pair->{ List<Holder<?>> values=new ArrayList<>(); pair.getSecond().forEach(values::add); observedTags.put(pair.getFirst(),values);}); for (var tag:expectedTags.entrySet()) { if(!observedTags.containsKey(tag.getKey()) || !observedTags.get(tag.getKey()).equals(tag.getValue())) throw new IllegalStateException("TAG_COMMIT_VERIFICATION_FAILED: "+tag.getKey()); } }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyRestoredTagRecipe(MinecraftServer server, ActiveTagRecipeGeneration generation, TagRecipeCommitTransaction tx){
        if (tx.recipeManagerIdentity() != 0 && tx.recipeManagerIdentity() != System.identityHashCode(server.getRecipeManager()))
            throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: recipe manager identity");
        Map<ResourceLocation, ActiveRecipeSnapshot> expectedRecipes = generation.recipes().recipes().stream()
                .collect(java.util.stream.Collectors.toMap(Recipe::getId, r -> ActiveRecipeSnapshot.capture(r, server.registryAccess()), (a,b)->a, LinkedHashMap::new));
        Map<ResourceLocation, ActiveRecipeSnapshot> actualRecipes = server.getRecipeManager().getRecipes().stream()
                .collect(java.util.stream.Collectors.toMap(Recipe::getId, r -> ActiveRecipeSnapshot.capture(r, server.registryAccess()), (a,b)-> {
                    throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: duplicate recipe");
                }, LinkedHashMap::new));
        if(!actualRecipes.equals(expectedRecipes)) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: recipe structure");
        for (Recipe<?> recipe : generation.recipes().recipes()) {
            List<Recipe<?>> indexed = ((RecipeManager) server.getRecipeManager()).getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) recipe.getType());
            long occurrences = indexed.stream().filter(r -> r.getId().equals(recipe.getId())).count();
            if (occurrences != 1) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: recipe type index");
            for (Recipe<?> other : server.getRecipeManager().getRecipes()) {
                if (!other.getType().equals(recipe.getType()) && other.getId().equals(recipe.getId()))
                    throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: recipe in wrong type index");
            }
        }
        for (var generationEntry : generation.tags().registries().entrySet()) {
            Registry<?> registry = server.registryAccess().registryOrThrow(generationEntry.getKey());
            var structural = generation.tags().snapshots().get(generationEntry.getKey());
            if (structural == null || (structural.registryIdentity() != 0 && structural.registryIdentity() != System.identityHashCode(registry))) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: registry identity");
            Map<TagKey<?>, List<Holder<?>>> observed = new LinkedHashMap<>();
            registry.getTags().forEach(pair -> { List<Holder<?>> values=new ArrayList<>(); pair.getSecond().forEach(values::add); observed.put(pair.getFirst(), List.copyOf(values)); });
            for (var tag : structural.tags().entrySet()) {
                TagKey key = TagKey.create((ResourceKey) generationEntry.getKey(), tag.getKey());
                boolean present = observed.containsKey(key);
                if (tag.getValue().state() == com.gabriel0liv.partialreload.tags.TagState.MISSING && present) throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: missing tag restored");
                if (tag.getValue().state() != com.gabriel0liv.partialreload.tags.TagState.MISSING) {
                    List<ResourceLocation> observedMembers = present ? observed.get(key).stream()
                            .map(h -> h.unwrapKey().map(k -> k.location()).orElse(null))
                            .sorted().toList() : List.of();
                    if (!present || !observedMembers.equals(tag.getValue().members()))
                        throw new IllegalStateException("TAG_RECIPE_ROLLBACK_VERIFICATION_FAILED: tag members");
                }
            }
        }
    }
    private void promoteTagRecipeBaseline(PreparedTagsAndRecipes artifact, Set<ResourceKey<? extends Registry<?>>> scope){if(activeReference==null)return; Map<ResourceLocation,com.gabriel0liv.partialreload.resource.ResourceDescriptor> merged=new LinkedHashMap<>(activeReference.resources()); Set<String> paths=scope.stream().map(ResourceKey::location).map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet()); merged.entrySet().removeIf(e->{var d=e.getValue(); if(d.category()==ReloadCategory.RECIPES)return true; if(d.category()!=ReloadCategory.TAGS)return false; String p=d.location().getPath(); if(!p.startsWith("tags/"))return false; String rest=p.substring(5); int slash=rest.indexOf('/'); if(slash<0)return false; String registry=rest.substring(0,slash); if(registry.equals("worldgen")){int second=rest.indexOf('/',slash+1); if(second>0)registry=rest.substring(0,second);} String canonical=canonicalRegistry(registry); return canonical!=null && paths.contains(canonical);}); artifact.sourceSnapshot().resources().forEach((id,d)->{if(d.category()==ReloadCategory.RECIPES)merged.put(id,d); else if(d.category()==ReloadCategory.TAGS){String p=d.location().getPath(); String rest=p.startsWith("tags/")?p.substring(5):""; int slash=rest.indexOf('/'); if(slash>0){String registry=rest.substring(0,slash); String canonical=canonicalRegistry(registry); if(canonical!=null && paths.contains(canonical)) merged.put(id,d);}}}); activeReference=new ResourceSnapshot(Instant.now(),merged); if(latestScan!=null)lastChangeSet=ChangeDetector.diff(activeReference,latestScan);}

    public synchronized void processFunctionSafePoint(MinecraftServer server) {
        if (transaction == null || transaction.status() != FunctionTransactionStatus.QUIESCING) return;
        FunctionCommitTransaction tx = transaction;
        tx.status(FunctionTransactionStatus.COMMITTING);
        stateMachine.transitionTo(PartialReloadState.COMMITTING);
        tx.event(Instant.now(), TransactionEventType.SAFE_POINT_REACHED, "ServerTickEvent.END");
        if (FunctionLibraryBridge.executionActive(server.getFunctions())) {
            tx.status(FunctionTransactionStatus.FAILED_SAFE);
            tx.event(Instant.now(), TransactionEventType.FAILED, "FUNCTION_APPLY_FROM_ACTIVE_CHAIN_REJECTED");
            stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
            return;
        }
        try {
            if (tx.preparationId() == null) {
                rollbackAtSafePoint(server, tx);
                return;
            }
            PreparedFunctions artifact = preparedArtifact instanceof PreparedFunctions p ? p : null;
            if (artifact == null || !artifact.preparationId().equals(tx.preparationId()))
                throw new IllegalStateException("FUNCTION_PREPARATION_STALE");
            FunctionGeneration previous = captureGeneration(server);
            tx.previousGeneration(previous);
            retainedGeneration = previous;
            tx.event(Instant.now(), TransactionEventType.PREVIOUS_GENERATION_CAPTURED, previous.generationId().toString());
            var candidateLibrary = FunctionLibraryBridge.buildCandidate(artifact);
            FunctionGeneration candidate = new FunctionGeneration(UUID.randomUUID(), Instant.now(),
                    artifact.sourceSnapshot(), candidateLibrary, artifact.functions().keySet(),
                    artifact.functionTags(), artifact.tickFunctions(), artifact.loadFunctions());
            tx.candidateGeneration(candidate);
            tx.event(Instant.now(), TransactionEventType.CANDIDATE_BUILT, candidate.generationId().toString());
            FunctionLibraryBridge.publishWithoutLoad(server.getFunctions(), candidateLibrary);
            tx.mutationOccurred(true);
            activeGeneration = candidate;
            tx.event(Instant.now(), TransactionEventType.LIBRARY_SWAPPED, "library");
            tx.event(Instant.now(), TransactionEventType.LOAD_SUPPRESSED, "DO_NOT_RUN");
            tx.event(Instant.now(), TransactionEventType.TICK_SET_UPDATED, artifact.tickFunctions().toString());
            tx.status(FunctionTransactionStatus.VERIFYING);
            stateMachine.transitionTo(PartialReloadState.VERIFYING);
            tx.event(Instant.now(), TransactionEventType.VERIFICATION_STARTED, "active manager");
            verify(server, candidate, artifact);
            tx.verificationPassed(true);
            tx.event(Instant.now(), TransactionEventType.VERIFICATION_PASSED, "library/tick/load");
            promoteFunctionBaseline(artifact);
            tx.event(Instant.now(), TransactionEventType.BASELINE_PROMOTED, "functions");
            preparedArtifact = null;
            tx.status(FunctionTransactionStatus.SUCCESS);
            tx.event(Instant.now(), TransactionEventType.SUCCESS, "FUNCTION_COMMIT_SUCCEEDED");
            stateMachine.transitionTo(PartialReloadState.SUCCESS);
        } catch (RuntimeException failure) {
            tx.event(Instant.now(), TransactionEventType.FAILED, failure.getMessage());
            try { rollbackAtSafePoint(server, tx); }
            catch (RuntimeException rollbackFailure) {
                tx.status(FunctionTransactionStatus.DEGRADED);
                tx.event(Instant.now(), TransactionEventType.DEGRADED, rollbackFailure.getMessage());
                stateMachine.transitionTo(PartialReloadState.DEGRADED);
            }
        }
    }

    private FunctionGeneration captureGeneration(MinecraftServer server) {
        var manager = server.getFunctions();
        Map<ResourceLocation, Set<ResourceLocation>> tags = new java.util.LinkedHashMap<>();
        manager.getTagNames().forEach(id -> tags.put(id, manager.getTag(id).stream()
                .map(CommandFunction::getId).collect(java.util.stream.Collectors.toUnmodifiableSet())));
        Set<ResourceLocation> ids = java.util.stream.StreamSupport.stream(manager.getFunctionNames().spliterator(), false)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ResourceLocation> tick = Set.copyOf(FunctionLibraryBridge.ticking(manager));
        Set<ResourceLocation> load = manager.getTag(VanillaFunctionsProvider.LOAD_TAG).stream()
                .map(CommandFunction::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new FunctionGeneration(UUID.randomUUID(), Instant.now(),
                activeReference == null
                        ? (latestScan == null ? new ResourceSnapshot(Instant.now(), Map.of()) : latestScan)
                        : activeReference,
                FunctionLibraryBridge.activeLibrary(manager), ids, tags, tick, load);
    }

    private void verify(MinecraftServer server, FunctionGeneration candidate, PreparedFunctions artifact) {
        var manager = server.getFunctions();
        if (FunctionLibraryBridge.activeLibrary(manager) != candidate.library()
                || FunctionLibraryBridge.loadPending(manager)
                || !Set.copyOf(FunctionLibraryBridge.ticking(manager)).equals(artifact.tickFunctions())) {
            throw new IllegalStateException("FUNCTION_VERIFICATION_FAILED");
        }
    }

    private void rollbackAtSafePoint(MinecraftServer server, FunctionCommitTransaction tx) {
        tx.event(Instant.now(), TransactionEventType.ROLLBACK_STARTED, "previous generation");
        if (tx.previousGeneration() == null) throw new IllegalStateException("FUNCTION_ROLLBACK_FAILED");
        FunctionLibraryBridge.publishWithoutLoad(server.getFunctions(), tx.previousGeneration().library());
        tx.mutationOccurred(true);
        tx.verificationPassed(true);
        activeGeneration = tx.previousGeneration();
        activeReference = tx.previousGeneration().snapshot();
        if (latestScan != null) lastChangeSet = ChangeDetector.diff(activeReference, latestScan);
        retainedGeneration = null;
        tx.status(FunctionTransactionStatus.ROLLED_BACK);
        tx.event(Instant.now(), TransactionEventType.ROLLBACK_COMPLETED, "FUNCTION_ROLLBACK_SUCCEEDED");
        stateMachine.transitionTo(PartialReloadState.ROLLED_BACK);
    }

    private void promoteFunctionBaseline(PreparedFunctions artifact) {
        if (activeReference == null) return;
        Map<ResourceLocation, com.gabriel0liv.partialreload.resource.ResourceDescriptor> merged = new java.util.LinkedHashMap<>(activeReference.resources());
        merged.entrySet().removeIf(entry -> entry.getValue().category() == ReloadCategory.FUNCTIONS);
        artifact.sourceSnapshot().resources().forEach((id, descriptor) -> {
            if (descriptor.category() == ReloadCategory.FUNCTIONS) merged.put(id, descriptor);
        });
        activeReference = new ResourceSnapshot(Instant.now(), merged);
        if (latestScan != null) lastChangeSet = ChangeDetector.diff(activeReference, latestScan);
    }

    public synchronized boolean discardPrepared() {
        PartialReloadState state = stateMachine.state();
        if (state == PartialReloadState.PREPARING || state == PartialReloadState.VALIDATING) {
            throw new IllegalStateException("A function preparation is still in progress");
        }
        boolean existed = preparedArtifact != null;
        preparedArtifact = null;
        if (state == PartialReloadState.READY || state == PartialReloadState.FAILED_SAFE
                || state == PartialReloadState.SUCCESS || state == PartialReloadState.ROLLED_BACK) {
            stateMachine.transitionTo(PartialReloadState.IDLE);
        } else if (state == PartialReloadState.DEGRADED) {
            throw new IllegalStateException("FUNCTION_COMMIT_DEGRADED: restart is required");
        }
        return existed;
    }

    private ReloadPlan plan(ChangeSet changes) {
        resetTerminalState();
        stateMachine.transitionTo(PartialReloadState.PLANNING);
        try {
            lastPlan = planner.createPlan(changes);
            lastError = null;
            stateMachine.transitionTo(PartialReloadState.READY);
            return lastPlan;
        } catch (RuntimeException exception) {
            lastError = exception.getMessage();
            stateMachine.transitionTo(PartialReloadState.FAILED_SAFE);
            throw exception;
        }
    }

    public synchronized PartialReloadStatus status() {
        return new PartialReloadStatus(
                stateMachine.state(),
                providerRegistry.all().size(),
                3,
                latestScan == null ? null : latestScan.scannedAt(),
                lastChangeSet.changedResources().size(),
                preparedArtifact == null ? null : preparedArtifact.preparationId(),
                preparedArtifact == null ? null : preparedArtifact.isApplicable(),
                lastError,
                deferredClientRefreshTracker.activeGeneration(),
                deferredClientRefreshTracker.lastCommitDeferred(),
                deferredClientRefreshTracker.staleCount(),
                activeLootDataGeneration == null ? null : activeLootDataGeneration.generationId(),
                lootDataTransaction == null ? "none" : lootDataTransaction.status().name(),
                retainedLootDataGeneration != null,
                activeLootDataGeneration == null ? 0 : activeLootDataGeneration.predicateCount(),
                activeLootDataGeneration == null ? 0 : activeLootDataGeneration.itemModifierCount(),
                activeLootDataGeneration == null ? 0 : activeLootDataGeneration.lootTableCount(),
                activeGlobalLootModifierGeneration == null ? null : activeGlobalLootModifierGeneration.generationId(),
                globalLootModifierTransaction == null ? "none" : globalLootModifierTransaction.status().name(),
                retainedGlobalLootModifierGeneration != null,
                activeGlobalLootModifierGeneration == null ? 0 : activeGlobalLootModifierGeneration.orderedModifiers().size(),
                lootAndGlmTransaction == null ? "none" : lootAndGlmTransaction.status().name()
        );
    }

    public synchronized ChangeSet lastChangeSet() {
        return lastChangeSet;
    }

    public synchronized ResourceSnapshot latestScan() {
        return latestScan;
    }

    public synchronized ResourceSnapshot activeReference() {
        return activeReference;
    }

    public ProviderRegistry providerRegistry() {
        return providerRegistry;
    }

    private void resetTerminalState() {
        PartialReloadState state = stateMachine.state();
        if (state == PartialReloadState.READY || state == PartialReloadState.FAILED_SAFE
                || state == PartialReloadState.SUCCESS || state == PartialReloadState.ROLLED_BACK) {
            stateMachine.transitionTo(PartialReloadState.IDLE);
        } else if (state == PartialReloadState.PREPARING
                || state == PartialReloadState.VALIDATING) {
            throw new IllegalStateException(
                    "PREPARATION_ALREADY_RUNNING: a global preparation is already in progress"
            );
        } else if (state != PartialReloadState.IDLE) {
            throw new InvalidStateTransitionException(state, PartialReloadState.IDLE);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
