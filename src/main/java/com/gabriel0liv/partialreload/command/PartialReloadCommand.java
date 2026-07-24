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
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

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
                .then(unsupported("apply"))
                .then(unsupported("reload"))
                .then(unsupported("rollback")));
    }

    private static int status(CommandSourceStack source, PartialReloadService service) {
        PartialReloadStatus status = service.status();
        source.sendSuccess(() -> Component.literal("Partial Reload " + PartialReloadMod.VERSION), false);
        source.sendSuccess(() -> Component.literal("Mode: READ_ONLY"), false);
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
                "Optional integrations absent in phase 1: kubejs, origins, silentgear"
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> unsupported(String name) {
        return Commands.literal(name)
                .executes(context -> unsupportedResponse(context.getSource()))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .executes(context -> unsupportedResponse(context.getSource())));
    }

    private static int unsupportedResponse(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "Commit is not implemented in phase 1. No reload or fallback was executed."
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
