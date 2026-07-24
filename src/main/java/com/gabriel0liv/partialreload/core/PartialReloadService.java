package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.api.ScanResult;
import com.gabriel0liv.partialreload.change.ChangeDetector;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.PreparedFunctions;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class PartialReloadService {
    private final ProviderRegistry providerRegistry;
    private final ReloadProvider scannerProvider;
    private final ReloadPlanner planner;
    private final VanillaFunctionsProvider functionsProvider;
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
    private PreparedFunctions preparedFunctions;

    public PartialReloadService(
            ProviderRegistry providerRegistry,
            ReloadProvider scannerProvider,
            ReloadPlanner planner,
            VanillaFunctionsProvider functionsProvider
    ) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.scannerProvider = Objects.requireNonNull(scannerProvider, "scannerProvider");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.functionsProvider = Objects.requireNonNull(functionsProvider, "functionsProvider");
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
                        preparedFunctions == null ? PartialReloadState.IDLE : PartialReloadState.READY
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
            preparedFunctions = null;
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
                preparedFunctions = artifact;
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
        return preparedFunctions;
    }

    public synchronized boolean discardPrepared() {
        PartialReloadState state = stateMachine.state();
        if (state == PartialReloadState.PREPARING || state == PartialReloadState.VALIDATING) {
            throw new IllegalStateException("A function preparation is still in progress");
        }
        boolean existed = preparedFunctions != null;
        preparedFunctions = null;
        if (state == PartialReloadState.READY || state == PartialReloadState.FAILED_SAFE) {
            stateMachine.transitionTo(PartialReloadState.IDLE);
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
                preparedFunctions == null ? null : preparedFunctions.preparationId(),
                preparedFunctions == null ? null : preparedFunctions.isApplicable(),
                lastError
        );
    }

    public synchronized ChangeSet lastChangeSet() {
        return lastChangeSet;
    }

    public synchronized ResourceSnapshot latestScan() {
        return latestScan;
    }

    public ProviderRegistry providerRegistry() {
        return providerRegistry;
    }

    private void resetTerminalState() {
        PartialReloadState state = stateMachine.state();
        if (state == PartialReloadState.READY || state == PartialReloadState.FAILED_SAFE) {
            stateMachine.transitionTo(PartialReloadState.IDLE);
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
