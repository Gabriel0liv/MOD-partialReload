package com.gabriel0liv.partialreload.command;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadEnvironment;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.change.ResourceChange;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.PartialReloadStatus;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.PreparedFunctions;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PartialReloadCommand {
    private PartialReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PartialReloadService service) {
        dispatcher.register(Commands.literal("partialreload")
                .requires(source -> source.hasPermission(PartialReloadConfig.commandPermissionLevel()))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), service)))
                .then(Commands.literal("categories").executes(context -> categories(context.getSource())))
                .then(Commands.literal("providers").executes(context -> providers(context.getSource(), service)))
                .then(Commands.literal("scan").executes(context -> scan(context.getSource(), service)))
                .then(Commands.literal("changed").executes(context -> changed(context.getSource(), service)))
                .then(Commands.literal("plan")
                        .then(Commands.literal("changed")
                                .executes(context -> showPlan(context.getSource(), service.planChanged())))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests((context, builder) -> suggestCategories(builder))
                                .executes(context -> planCategory(
                                        context.getSource(),
                                        service,
                                        StringArgumentType.getString(context, "category")
                                ))))
                .then(Commands.literal("prepare")
                        .then(Commands.literal("changed")
                                .executes(context -> prepareFunctions(context.getSource(), service, true)))
                        .then(Commands.literal("functions")
                                .executes(context -> prepareFunctions(context.getSource(), service, false))))
                .then(Commands.literal("prepared")
                        .executes(context -> prepared(context.getSource(), service)))
                .then(Commands.literal("discard")
                        .executes(context -> discard(context.getSource(), service)))
                .then(unsupported("apply"))
                .then(unsupported("reload"))
                .then(unsupported("rollback")));
    }

    private static int status(CommandSourceStack source, PartialReloadService service) {
        PartialReloadStatus status = service.status();
        source.sendSuccess(() -> Component.literal("Partial Reload " + PartialReloadMod.VERSION), false);
        source.sendSuccess(() -> Component.literal("Mode: PREPARE_ONLY"), false);
        source.sendSuccess(() -> Component.literal("State: " + status.state()), false);
        source.sendSuccess(() -> Component.literal(
                "Providers: " + status.registeredProviders() + " compatible, "
                        + status.plannedIntegrations() + " planned integrations"
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Last scan: " + (status.lastScanAt() == null ? "never" : status.lastScanAt())
        ), false);
        source.sendSuccess(() -> Component.literal("Changed resources: " + status.changedResources()), false);
        source.sendSuccess(() -> Component.literal("Apply support: not implemented"), false);
        source.sendSuccess(() -> Component.literal(
                "Prepared artifact: " + (status.preparedId() == null
                        ? "none"
                        : status.preparedId() + " (technically applicable: " + status.preparedApplicable() + ")")
        ), false);
        if (status.lastError() != null) {
            source.sendFailure(Component.literal("Last error: " + status.lastError()));
        }
        return 1;
    }

    private static int categories(CommandSourceStack source) {
        for (ReloadCategory category : ReloadCategory.values()) {
            String support = switch (category) {
                case DYNAMIC_REGISTRIES -> "RESTART_REQUIRED";
                case UNKNOWN -> "UNKNOWN";
                case FUNCTIONS -> "PREPARE_SUPPORTED";
                case PREDICATES -> "PREDICATES_COUPLED_TO_LOOT";
                case ORIGINS, KUBEJS, SILENTGEAR -> "PLANNED";
                default -> "SUPPORTED_READ_ONLY";
            };
            source.sendSuccess(() -> Component.literal(category.commandName() + " - " + support), false);
        }
        return ReloadCategory.values().length;
    }

    private static int providers(CommandSourceStack source, PartialReloadService service) {
        ReloadEnvironment environment = new ReloadEnvironment(source.getServer().isDedicatedServer(), Set.of());
        service.providerRegistry().all().forEach(provider -> source.sendSuccess(
                () -> Component.literal(provider.id() + " - " + provider.compatibility(environment)
                        + " - " + provider.categories().size() + " categories"),
                false
        ));
        source.sendSuccess(() -> Component.literal(
                "Optional integrations absent: kubejs, origins, silentgear"
        ), false);
        return service.providerRegistry().all().size();
    }

    private static int scan(CommandSourceStack source, PartialReloadService service) {
        ScanContext scanContext = new ScanContext(
                source.getServer().getResourceManager(),
                PartialReloadConfig.maxScannedResources(),
                Duration.ofSeconds(PartialReloadConfig.scanTimeoutSeconds()),
                PartialReloadConfig.enableUnknownResourceReporting()
        );
        source.sendSuccess(() -> Component.literal("Partial Reload scan started (read-only)."), false);
        service.scanAsync(scanContext, Util.backgroundExecutor(), source.getServer())
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        PartialReloadMod.LOGGER.error("Partial Reload resource scan failed", unwrap(throwable));
                        source.sendFailure(Component.literal("Scan failed safely: " + rootMessage(throwable)));
                        return;
                    }
                    long unknown = result.snapshot().resources().values().stream()
                            .filter(resource -> resource.category() == ReloadCategory.UNKNOWN)
                            .count();
                    String unknownSummary = PartialReloadConfig.enableUnknownResourceReporting()
                            ? ", " + unknown + " unknown."
                            : ", unknown reporting disabled.";
                    source.sendSuccess(() -> Component.literal(
                            "Scan complete: " + result.snapshot().resources().size()
                                    + " resources, " + result.snapshot().namespaces().size()
                                    + " namespaces" + unknownSummary
                    ), false);
                });
        return 1;
    }

    private static int changed(CommandSourceStack source, PartialReloadService service) {
        if (service.latestScan() == null) {
            source.sendFailure(Component.literal("No scan is available. Run /partialreload scan first."));
            return 0;
        }
        List<ResourceChange> changes = service.lastChangeSet().changedResources();
        source.sendSuccess(() -> Component.literal("Changed resources: " + changes.size()), false);
        if (PartialReloadConfig.logResourceDetails()) {
            changes.stream().limit(100).forEach(change -> source.sendSuccess(
                    () -> Component.literal(change.kind() + " " + change.location()
                            + " [" + change.category().commandName() + "]"),
                    false
            ));
        }
        return changes.size();
    }

    private static int planCategory(
            CommandSourceStack source,
            PartialReloadService service,
            String categoryName
    ) {
        return ReloadCategory.fromCommandName(categoryName)
                .map(category -> showPlan(source, service.planCategory(category)))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal("Unknown reload category: " + categoryName));
                    return 0;
                });
    }

    private static int showPlan(CommandSourceStack source, ReloadPlan plan) {
        source.sendSuccess(() -> Component.literal("Plan " + plan.id()), false);
        source.sendSuccess(() -> Component.literal("Created: " + plan.createdAt()), false);
        source.sendSuccess(() -> Component.literal("Categories: " + plan.categories().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(ReloadCategory::commandName)
                .toList()), false);
        source.sendSuccess(() -> Component.literal("Changed resources: " + plan.changedResources().size()), false);
        source.sendSuccess(() -> Component.literal("Risk: " + plan.risk()), false);
        source.sendSuccess(() -> Component.literal("Support: " + plan.supportStatus()), false);
        plan.warnings().forEach(warning ->
                source.sendSuccess(() -> Component.literal("Warning: " + warning), false));
        plan.blockers().forEach(blocker -> source.sendFailure(Component.literal("Blocker: " + blocker)));
        source.sendFailure(Component.literal("Apply support: APPLY_NOT_IMPLEMENTED"));
        return 1;
    }

    private static int prepareFunctions(
            CommandSourceStack source,
            PartialReloadService service,
            boolean changedOnly
    ) {
        if (changedOnly && !service.hasFunctionChanges()) {
            source.sendFailure(Component.literal(
                    "No changed function sources or function tags are present in the current ChangeSet."
            ));
            return 0;
        }
        var manager = source.getServer().getFunctions();
        Set<net.minecraft.resources.ResourceLocation> activeTick = manager
                .getTag(VanillaFunctionsProvider.TICK_TAG)
                .stream()
                .map(net.minecraft.commands.CommandFunction::getId)
                .collect(Collectors.toUnmodifiableSet());
        Set<net.minecraft.resources.ResourceLocation> activeLoad = manager
                .getTag(VanillaFunctionsProvider.LOAD_TAG)
                .stream()
                .map(net.minecraft.commands.CommandFunction::getId)
                .collect(Collectors.toUnmodifiableSet());
        FunctionPreparationContext preparationContext = new FunctionPreparationContext(
                source.getServer().getResourceManager(),
                source.getServer().getCommands().getDispatcher(),
                source.getServer().getFunctionCompilationLevel(),
                activeTick,
                activeLoad,
                Duration.ofSeconds(PartialReloadConfig.prepareTimeoutSeconds()),
                PartialReloadConfig.maxScannedResources(),
                PartialReloadConfig.maxFunctionCount(),
                PartialReloadConfig.maxFunctionLines(),
                Clock.systemUTC(),
                UUID::randomUUID,
                System::nanoTime
        );
        try {
            service.prepareFunctionsAsync(
                    preparationContext,
                    Util.backgroundExecutor(),
                    source.getServer()
            ).whenComplete((artifact, throwable) -> {
                if (throwable != null) {
                    PartialReloadMod.LOGGER.error(
                            "Function preparation failed safely [provider={}]",
                            VanillaFunctionsProvider.ID,
                            unwrap(throwable)
                    );
                    source.sendFailure(Component.literal(
                            "Function preparation failed safely: " + rootMessage(throwable)
                    ));
                    return;
                }
                showPrepared(source, artifact);
            });
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Preparation rejected: " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Function preparation started. Active function manager remains unchanged."
        ), false);
        return 1;
    }

    private static int prepared(CommandSourceStack source, PartialReloadService service) {
        PreparedFunctions artifact = service.preparedFunctions();
        if (artifact == null) {
            source.sendFailure(Component.literal("No prepared function artifact is available."));
            return 0;
        }
        return showPrepared(source, artifact);
    }

    private static int showPrepared(CommandSourceStack source, PreparedFunctions artifact) {
        source.sendSuccess(() -> Component.literal("Preparation #" + artifact.preparationId()), false);
        source.sendSuccess(() -> Component.literal("Category: functions"), false);
        source.sendSuccess(() -> Component.literal(
                "Functions compiled: " + artifact.functions().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Function tags resolved: " + artifact.functionTags().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Tick functions: " + artifact.tickFunctions().size()
                        + " (+" + artifact.tickDelta().added().size()
                        + " -" + artifact.tickDelta().removed().size() + ")"
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Load functions: " + artifact.loadFunctions().size()
                        + " (+" + artifact.loadDelta().added().size()
                        + " -" + artifact.loadDelta().removed().size() + ")"
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Dependencies: " + artifact.dependencyGraph().dependencies().size()
        ), false);
        long warnings = artifact.validation().count(
                com.gabriel0liv.partialreload.validation.ValidationSeverity.WARNING
        );
        long errors = artifact.validation().issues().stream()
                .filter(issue -> issue.severity()
                        == com.gabriel0liv.partialreload.validation.ValidationSeverity.ERROR
                        || issue.severity()
                        == com.gabriel0liv.partialreload.validation.ValidationSeverity.BLOCKER)
                .count();
        source.sendSuccess(() -> Component.literal(
                "Warnings: " + warnings + ", errors/blockers: " + errors
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Technically applicable: " + artifact.isApplicable()
        ), false);
        artifact.validation().issues().stream().limit(20).forEach(issue -> {
            String location = issue.sourceLocation() == null
                    ? ""
                    : " line " + issue.sourceLocation().line()
                    + ": " + issue.sourceLocation().command();
            if (issue.severity()
                    == com.gabriel0liv.partialreload.validation.ValidationSeverity.INFO
                    || issue.severity()
                    == com.gabriel0liv.partialreload.validation.ValidationSeverity.WARNING) {
                source.sendSuccess(() -> Component.literal(
                        issue.severity() + " " + issue.code() + " "
                                + (issue.resource() == null ? "" : issue.resource())
                                + location + " — " + issue.message()
                ), false);
            } else {
                source.sendFailure(Component.literal(
                        issue.severity() + " " + issue.code() + " "
                                + (issue.resource() == null ? "" : issue.resource())
                                + location + " — " + issue.message()
                ));
            }
        });
        source.sendFailure(Component.literal("Commit support: not implemented"));
        source.sendSuccess(() -> Component.literal("Active function manager: unchanged"), false);
        return 1;
    }

    private static int discard(CommandSourceStack source, PartialReloadService service) {
        try {
            boolean discarded = service.discardPrepared();
            source.sendSuccess(() -> Component.literal(
                    discarded ? "Prepared function artifact discarded." : "No prepared artifact existed."
            ), false);
            return discarded ? 1 : 0;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Discard rejected: " + exception.getMessage()));
            return 0;
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> unsupported(String name) {
        return Commands.literal(name)
                .executes(context -> unsupportedResponse(context.getSource()))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .executes(context -> unsupportedResponse(context.getSource())));
    }

    private static int unsupportedResponse(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "Commit is not implemented. No reload or fallback was executed."
        ));
        return 0;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestCategories(SuggestionsBuilder builder) {
        Arrays.stream(ReloadCategory.values()).map(ReloadCategory::commandName).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException && throwable.getCause() != null
                ? unwrap(throwable.getCause())
                : throwable;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = unwrap(throwable);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
