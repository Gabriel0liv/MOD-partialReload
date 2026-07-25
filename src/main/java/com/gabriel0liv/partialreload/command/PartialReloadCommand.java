package com.gabriel0liv.partialreload.command;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadEnvironment;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.change.ResourceChange;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.PartialReloadStatus;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.function.FunctionPreparationContext;
import com.gabriel0liv.partialreload.function.PreparedFunctions;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.loot.LootPreparationContext;
import com.gabriel0liv.partialreload.loot.PreparedLootData;
import com.gabriel0liv.partialreload.loot.VanillaLootDataProvider;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.tags.PreparedTags;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
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
                                .executes(context -> prepareChanged(context.getSource(), service)))
                        .then(Commands.literal("functions")
                                .executes(context -> prepareFunctions(context.getSource(), service, false)))
                        .then(Commands.literal("predicates")
                                .executes(context -> prepareLootData(
                                        context.getSource(), service, ReloadCategory.PREDICATES, false)))
                        .then(Commands.literal("item_modifiers")
                                .executes(context -> prepareLootData(
                                        context.getSource(), service, ReloadCategory.ITEM_MODIFIERS, false)))
                        .then(Commands.literal("loot")
                                .executes(context -> prepareLootData(
                                        context.getSource(), service, ReloadCategory.LOOT, false)))
                        .then(Commands.literal("recipes")
                                .executes(context -> prepareRecipes(context.getSource(), service, false)))
                        .then(Commands.literal("tags")
                                .executes(context -> prepareTags(context.getSource(), service, false))))
                .then(Commands.literal("prepared")
                        .executes(context -> prepared(context.getSource(), service)))
                .then(Commands.literal("discard")
                        .executes(context -> discard(context.getSource(), service)))
                .then(Commands.literal("apply")
                        .then(Commands.literal("prepared")
                                .executes(context -> applyPrepared(context.getSource(), service))))
                .then(Commands.literal("transaction")
                        .executes(context -> transaction(context.getSource(), service)))
                .then(Commands.literal("rollback")
                        .then(Commands.literal("functions")
                                .executes(context -> rollbackFunctions(context.getSource(), service))))
                .then(Commands.literal("active")
                        .then(Commands.literal("functions")
                                .executes(context -> activeFunctions(context.getSource()))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("manager_fingerprints")
                                .requires(source -> !net.minecraftforge.fml.loading.FMLEnvironment.production)
                                .executes(context -> managerFingerprints(context.getSource()))))
                .then(unsupported("reload"))
                );
    }

    private static int status(CommandSourceStack source, PartialReloadService service) {
        PartialReloadStatus status = service.status();
        source.sendSuccess(() -> Component.literal("Partial Reload " + PartialReloadMod.VERSION), false);
        String mode = service.functionCommitCompatibility(source.getServer()).compatible()
                ? "FUNCTION_COMMIT_SUPPORTED" : "PREPARE_ONLY";
        source.sendSuccess(() -> Component.literal("Mode: " + mode), false);
        source.sendSuccess(() -> Component.literal("State: " + status.state()), false);
        source.sendSuccess(() -> Component.literal(
                "Providers: " + status.registeredProviders() + " compatible, "
                        + status.plannedIntegrations() + " planned integrations"
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Last scan: " + (status.lastScanAt() == null ? "never" : status.lastScanAt())
        ), false);
        source.sendSuccess(() -> Component.literal("Changed resources: " + status.changedResources()), false);
        source.sendSuccess(() -> Component.literal("Loot data commit: not implemented"), false);
        source.sendSuccess(() -> Component.literal("Recipe commit: not implemented"), false);
        source.sendSuccess(() -> Component.literal("Tag commit: not implemented"), false);
        source.sendSuccess(() -> Component.literal("KubeJS recipe preparation: blocked"), false);
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
                case FUNCTIONS, PREDICATES, ITEM_MODIFIERS, LOOT, TAGS -> "PREPARE_SUPPORTED";
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

    private static int prepareChanged(CommandSourceStack source, PartialReloadService service) {
        if (service.hasTagChanges() && service.lastChangeSet().changedResources().stream().anyMatch(c -> c.category() != ReloadCategory.TAGS)) {
            source.sendFailure(Component.literal("Changed resources span tags and other categories. Select an explicit category; joint tag composition is not implemented."));
            return 0;
        }
        if (service.hasTagChanges()) return prepareTags(source, service, true);
        if (service.hasRecipeChanges() && service.hasMixedRecipeChanges()) {
            source.sendFailure(Component.literal("Changed resources span recipes and other categories. Select an explicit category."));
            return 0;
        }
        if (service.hasRecipeChanges()) return prepareRecipes(source, service, true);
        if (service.hasMixedFunctionAndLootChanges()) {
            source.sendFailure(Component.literal(
                    "Changed resources span functions and loot data. Select an explicit category; "
                            + "only one global preparation may run at a time."
            ));
            return 0;
        }
        if (service.hasFunctionChanges()) return prepareFunctions(source, service, true);
        if (service.hasLootDataChanges()) {
            Set<ReloadCategory> changed = service.lastChangeSet().changedResources().stream()
                    .map(ResourceChange::category)
                    .filter(PreparedLootData.COMPLETE_SCOPE::contains)
                    .collect(Collectors.toUnmodifiableSet());
            ReloadCategory requested = changed.stream().findFirst().orElse(ReloadCategory.LOOT);
            return prepareLootData(source, service, requested, true);
        }
        source.sendFailure(Component.literal(
                "No changed functions, tags, recipes or loot data are present in the current ChangeSet."
        ));
        return 0;
    }

    private static int prepareRecipes(CommandSourceStack source, PartialReloadService service, boolean changedOnly) {
        if (changedOnly && !service.hasRecipeChanges()) {
            source.sendFailure(Component.literal("No changed recipes are present.")); return 0;
        }
        try {
            service.prepareRecipesAsync(source.getServer().getResourceManager(), Util.backgroundExecutor(), source.getServer())
                    .whenComplete((artifact, throwable) -> {
                        if (throwable != null) source.sendFailure(Component.literal("Recipe preparation failed safely: " + rootMessage(throwable)));
                        else showPreparedRecipes(source, artifact);
                    });
        } catch (RuntimeException exception) { source.sendFailure(Component.literal("Preparation rejected: " + exception.getMessage())); return 0; }
        source.sendSuccess(() -> Component.literal("Recipe preparation started. Active RecipeManager remains unchanged."), false);
        return 1;
    }

    private static int prepareTags(CommandSourceStack source, PartialReloadService service, boolean changedOnly) {
        if (changedOnly && !service.hasTagChanges()) { source.sendFailure(Component.literal("No changed tags are present.")); return 0; }
        try {
            service.prepareTagsAsync(source.getServer().getResourceManager(), source.getServer().registryAccess(), Util.backgroundExecutor(), source.getServer())
                    .whenComplete((artifact, throwable) -> {
                        if (throwable != null) source.sendFailure(Component.literal("Tag preparation failed safely: " + rootMessage(throwable)));
                        else showPreparedTags(source, artifact);
                    });
        } catch (RuntimeException exception) { source.sendFailure(Component.literal("Preparation rejected: " + exception.getMessage())); return 0; }
        source.sendSuccess(() -> Component.literal("Tag preparation started. Active tag bindings remain unchanged."), false);
        return 1;
    }

    private static int showPreparedRecipes(CommandSourceStack source, PreparedRecipes artifact) {
        source.sendSuccess(() -> Component.literal("PreparedRecipes #" + artifact.preparationId()), false);
        source.sendSuccess(() -> Component.literal("Recipes discovered: " + artifact.discoveredRecipes()), false);
        source.sendSuccess(() -> Component.literal("Recipes prepared: " + artifact.preparedRecipes()), false);
        source.sendSuccess(() -> Component.literal("Recipes skipped by conditions: " + artifact.skippedByCondition()), false);
        source.sendSuccess(() -> Component.literal("Serializers used: " + artifact.serializersUsed().size()
                + ", Recipe types used: " + artifact.recipeTypesUsed().size()), false);
        source.sendSuccess(() -> Component.literal("Warnings: " + artifact.validation().count(ValidationSeverity.WARNING)
                + ", Errors: " + artifact.validation().count(ValidationSeverity.ERROR)), false);
        source.sendSuccess(() -> Component.literal("Technically applicable: " + artifact.isApplicable()), false);
        source.sendSuccess(() -> Component.literal("KubeJS integration: not loaded (Forge 1.20.1 runtime unavailable)"), false);
        source.sendSuccess(() -> Component.literal("Commit support: not implemented for recipes"), false);
        source.sendSuccess(() -> Component.literal("Active RecipeManager: unchanged"), false);
        return 1;
    }

    private static int showPreparedTags(CommandSourceStack source, PreparedTags artifact) {
        source.sendSuccess(() -> Component.literal("PreparedTags #" + artifact.preparationId()), false);
        source.sendSuccess(() -> Component.literal("Tag files discovered: " + artifact.discoveredFiles()), false);
        source.sendSuccess(() -> Component.literal("Registries prepared: " + artifact.registries().size()), false);
        source.sendSuccess(() -> Component.literal("Tags prepared: " + artifact.preparedTags() + ", resolved members: " + artifact.resolvedMembers()), false);
        source.sendSuccess(() -> Component.literal("Dependency edges: " + artifact.dependencyGraph().edgeCount()), false);
        source.sendSuccess(() -> Component.literal("Warnings: " + artifact.validation().count(ValidationSeverity.WARNING) + ", errors/blockers: " + artifact.validation().issues().stream().filter(i -> i.severity() == ValidationSeverity.ERROR || i.severity() == ValidationSeverity.BLOCKER).count()), false);
        source.sendSuccess(() -> Component.literal("Technically applicable: " + artifact.isApplicable()), false);
        artifact.validation().issues().stream().limit(30).forEach(issue ->
                source.sendFailure(Component.literal(issue.severity() + " " + issue.code() + " " + issue.message())));
        artifact.validation().issues().stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR || issue.severity() == ValidationSeverity.BLOCKER).limit(20).forEach(issue ->
                source.sendFailure(Component.literal(issue.severity() + " " + issue.code() + " " + issue.message())));
        source.sendSuccess(() -> Component.literal("Binding support: not implemented"), false);
        source.sendSuccess(() -> Component.literal("Active registry tags: unchanged"), false);
        return 1;
    }

    private static int prepareLootData(
            CommandSourceStack source,
            PartialReloadService service,
            ReloadCategory requested,
            boolean changedOnly
    ) {
        if (changedOnly && !service.hasLootDataChanges()) {
            source.sendFailure(Component.literal("No changed loot data are present."));
            return 0;
        }
        LootPreparationContext preparationContext = new LootPreparationContext(
                source.getServer().getResourceManager(),
                Set.of(requested),
                service.activeReference(),
                Duration.ofSeconds(PartialReloadConfig.lootPrepareTimeoutSeconds()),
                PartialReloadConfig.maxPredicates(),
                PartialReloadConfig.maxItemModifiers(),
                PartialReloadConfig.maxLootTables(),
                PartialReloadConfig.maxTotalJsonBytes(),
                PartialReloadConfig.maxDependencyEdges(),
                Clock.systemUTC(),
                UUID::randomUUID,
                System::nanoTime
        );
        try {
            service.prepareLootDataAsync(
                    preparationContext,
                    Util.backgroundExecutor(),
                    source.getServer()
            ).whenComplete((artifact, throwable) -> {
                if (throwable != null) {
                    PartialReloadMod.LOGGER.error(
                            "Loot data preparation failed safely [provider={}]",
                            VanillaLootDataProvider.ID,
                            unwrap(throwable)
                    );
                    source.sendFailure(Component.literal(
                            "Loot data preparation failed safely: " + rootMessage(throwable)
                    ));
                    return;
                }
                showPreparedLoot(source, artifact);
            });
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Preparation rejected: " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Joint loot data preparation started for requested category "
                        + requested.commandName() + ". Active LootDataManager remains unchanged."
        ), false);
        return 1;
    }

    private static int prepared(CommandSourceStack source, PartialReloadService service) {
        PreparedReloadArtifact artifact = service.preparedArtifact();
        if (artifact == null) {
            source.sendFailure(Component.literal("No prepared artifact is available."));
            return 0;
        }
        if (artifact instanceof PreparedFunctions functions) return showPrepared(source, functions);
        if (artifact instanceof PreparedLootData lootData) return showPreparedLoot(source, lootData);
        if (artifact instanceof PreparedRecipes recipes) return showPreparedRecipes(source, recipes);
        if (artifact instanceof PreparedTags tags) return showPreparedTags(source, tags);
        source.sendFailure(Component.literal("Unknown prepared artifact type."));
        return 0;
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
        boolean compatible = com.gabriel0liv.partialreload.function.FunctionCommitCompatibility
                .inspect(source.getServer()).compatible();
        source.sendSuccess(() -> Component.literal("Commit support: "
                + (compatible ? "functions supported" : "not compatible")), false);
        source.sendSuccess(() -> Component.literal("Active function manager: unchanged"), false);
        return 1;
    }

    private static int showPreparedLoot(CommandSourceStack source, PreparedLootData artifact) {
        source.sendSuccess(() -> Component.literal("Preparation #" + artifact.preparationId()), false);
        source.sendSuccess(() -> Component.literal(
                "Requested categories: " + artifact.requestedCategories().stream()
                        .map(ReloadCategory::commandName).sorted().toList()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Expanded scope: " + artifact.expandedCategories().stream()
                        .map(ReloadCategory::commandName).sorted().toList()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Changed resources: " + artifact.delta().changedCount()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Predicates prepared: " + artifact.predicates().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Item modifiers prepared: " + artifact.itemModifiers().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Loot tables prepared: " + artifact.lootTables().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Dependency edges: " + artifact.dependencyGraph().dependencies().size()
        ), false);
        long unsupported = artifact.validation().issues().stream()
                .filter(issue -> issue.code().equals("LOOT_EXTERNAL_PROVIDER_UNSUPPORTED"))
                .count();
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
                "Unsupported external resources: " + unsupported
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Warnings: " + warnings + ", errors/blockers: " + errors
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Technically applicable: " + artifact.isApplicable()
        ), false);
        artifact.validation().issues().stream().limit(20).forEach(issue -> {
            Component message = Component.literal(
                    issue.severity() + " " + issue.code() + " "
                            + (issue.resource() == null ? "" : issue.resource())
                            + " — " + issue.message()
            );
            if (issue.severity() == com.gabriel0liv.partialreload.validation.ValidationSeverity.ERROR
                    || issue.severity()
                    == com.gabriel0liv.partialreload.validation.ValidationSeverity.BLOCKER) {
                source.sendFailure(message);
            } else {
                source.sendSuccess(() -> message, false);
            }
        });
        source.sendFailure(Component.literal("Commit support: not implemented"));
        source.sendSuccess(() -> Component.literal("Active LootDataManager: unchanged"), false);
        return 1;
    }

    private static int discard(CommandSourceStack source, PartialReloadService service) {
        try {
            boolean discarded = service.discardPrepared();
            source.sendSuccess(() -> Component.literal(
                    discarded ? "Prepared artifact discarded." : "No prepared artifact existed."
            ), false);
            return discarded ? 1 : 0;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Discard rejected: " + exception.getMessage()));
            return 0;
        }
    }

    private static int applyPrepared(CommandSourceStack source, PartialReloadService service) {
        try {
            var tx = service.requestFunctionCommit(source.getServer(), source.getTextName());
            source.sendSuccess(() -> Component.literal(
                    "Function transaction " + tx.transactionId() + " queued for the next safe point."), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int transaction(CommandSourceStack source, PartialReloadService service) {
        var tx = service.transaction();
        if (tx == null) {
            source.sendFailure(Component.literal("No function transaction recorded."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Transaction: " + tx.transactionId()), false);
        source.sendSuccess(() -> Component.literal("Preparation: " + tx.preparationId()), false);
        source.sendSuccess(() -> Component.literal("Status: " + tx.status()), false);
        source.sendSuccess(() -> Component.literal("Load policy: " + tx.policy()), false);
        source.sendSuccess(() -> Component.literal(
                "Mutation occurred: " + tx.mutationOccurred()), false);
        source.sendSuccess(() -> Component.literal("Verification: " + tx.verificationPassed()), false);
        source.sendSuccess(() -> Component.literal(
                "Rollback retained: " + (service.retainedGeneration() != null)), false);
        return 1;
    }

    private static int rollbackFunctions(CommandSourceStack source, PartialReloadService service) {
        try {
            var tx = service.requestManualRollback(source.getTextName());
            source.sendSuccess(() -> Component.literal(
                    "Rollback transaction " + tx.transactionId() + " queued for the next safe point."), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int activeFunctions(CommandSourceStack source) {
        var manager = source.getServer().getFunctions();
        long functions = java.util.stream.StreamSupport.stream(
                manager.getFunctionNames().spliterator(), false).count();
        long tags = java.util.stream.StreamSupport.stream(
                manager.getTagNames().spliterator(), false).count();
        source.sendSuccess(() -> Component.literal("Active functions: " + functions), false);
        source.sendSuccess(() -> Component.literal("Active tags: " + tags), false);
        source.sendSuccess(() -> Component.literal(
                "Tick functions: " + com.gabriel0liv.partialreload.function.FunctionLibraryBridge
                        .ticking(manager).size()), false);
        source.sendSuccess(() -> Component.literal("Load pending: " +
                com.gabriel0liv.partialreload.function.FunctionLibraryBridge.loadPending(manager)), false);
        return 1;
    }

    private static int managerFingerprints(CommandSourceStack source) {
        var server = source.getServer();
        source.sendSuccess(() -> Component.literal("FunctionManager: "
                + System.identityHashCode(server.getFunctions())), false);
        source.sendSuccess(() -> Component.literal("FunctionLibrary: "
                + System.identityHashCode(com.gabriel0liv.partialreload.function.FunctionLibraryBridge
                .activeLibrary(server.getFunctions()))), false);
        source.sendSuccess(() -> Component.literal("LootDataManager: "
                + System.identityHashCode(server.getLootData())), false);
        source.sendSuccess(() -> Component.literal("RecipeManager: "
                + System.identityHashCode(server.getRecipeManager())), false);
        source.sendSuccess(() -> Component.literal("AdvancementManager: "
                + System.identityHashCode(server.getAdvancements())), false);
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
