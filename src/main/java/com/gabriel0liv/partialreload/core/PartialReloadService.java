package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
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
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.recipe.VanillaRecipesProvider;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.tags.VanillaTagsProvider;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class PartialReloadService {
    private final ProviderRegistry providerRegistry;
    private final ReloadProvider scannerProvider;
    private final ReloadPlanner planner;
    private final VanillaFunctionsProvider functionsProvider;
    private final VanillaLootDataProvider lootDataProvider;
    private final VanillaRecipesProvider recipesProvider = new VanillaRecipesProvider();
    private final VanillaTagsProvider tagsProvider = new VanillaTagsProvider();
    private final PartialReloadStateMachine stateMachine = new PartialReloadStateMachine();

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

    public PartialReloadService(
            ProviderRegistry providerRegistry,
            ReloadProvider scannerProvider,
            ReloadPlanner planner,
            VanillaFunctionsProvider functionsProvider,
            VanillaLootDataProvider lootDataProvider
    ) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.scannerProvider = Objects.requireNonNull(scannerProvider, "scannerProvider");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.functionsProvider = Objects.requireNonNull(functionsProvider, "functionsProvider");
        this.lootDataProvider = Objects.requireNonNull(lootDataProvider, "lootDataProvider");
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

    public synchronized boolean hasLootDataChanges() {
        return lastChangeSet.changedResources().stream()
                .anyMatch(change -> PreparedLootData.COMPLETE_SCOPE.contains(change.category()));
    }

    public synchronized boolean hasMixedFunctionAndLootChanges() {
        return hasFunctionChanges() && hasLootDataChanges();
    }

    public synchronized PreparedLootData preparedLootData() {
        return preparedArtifact instanceof PreparedLootData lootData ? lootData : null;
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
                lastError
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
