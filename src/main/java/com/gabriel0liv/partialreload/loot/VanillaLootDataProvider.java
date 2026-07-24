package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.api.PartialReloadException;
import com.gabriel0liv.partialreload.api.PlanningContext;
import com.gabriel0liv.partialreload.api.ProviderCompatibility;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadEnvironment;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.api.ScanResult;
import com.gabriel0liv.partialreload.api.ValidationContext;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ProviderPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.validation.SourceLocation;
import com.gabriel0liv.partialreload.validation.ValidationDetails;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataResolver;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.ForgeHooks;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VanillaLootDataProvider implements ReloadProvider {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("partialreload", "vanilla_loot_data");
    private static final Pattern ROOT =
            Pattern.compile("^\\{(predicates|item_modifiers|loot_tables):([^}]+)}");

    private final ResourceScanner scanner;
    private final LootResourceLoader loader = new LootResourceLoader();
    private final LootGraphExtractor graphExtractor = new LootGraphExtractor();
    private final LootDeltaCalculator deltaCalculator = new LootDeltaCalculator();

    public VanillaLootDataProvider(ResourceScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Set<ReloadCategory> categories() {
        return PreparedLootData.COMPLETE_SCOPE;
    }

    @Override
    public ProviderCompatibility compatibility(ReloadEnvironment environment) {
        return ProviderCompatibility.PREPARE_SUPPORTED;
    }

    @Override
    public ScanResult scan(ScanContext context) throws PartialReloadException {
        return new ScanResult(scanner.scan(context));
    }

    @Override
    public ValidationReport validate(ValidationContext context, ChangeSet changeSet) {
        return ValidationReport.VALID;
    }

    @Override
    public ProviderPlan createPlan(PlanningContext context, ChangeSet changeSet) {
        ChangeSet selected = new ChangeSet(changeSet.changedResources().stream()
                .filter(change -> PreparedLootData.COMPLETE_SCOPE.contains(change.category()))
                .toList());
        ReloadPlan plan = new ReloadPlanner(context.clock(), UUID::randomUUID).createPlan(selected);
        Set<String> dependencies = new LinkedHashSet<>(plan.dependencies());
        dependencies.add("joint LootDataManager candidate: predicates + item_modifiers + loot");
        List<String> warnings = new ArrayList<>(plan.warnings());
        warnings.add("Selected loot category expands to the complete shared validation graph");
        return new ProviderPlan(
                ID,
                plan.categories(),
                plan.changedResources(),
                plan.risk(),
                dependencies,
                warnings,
                plan.blockers(),
                plan.supportStatus()
        );
    }

    public PreparedLootData prepare(LootPreparationContext context)
            throws LootPreparationException {
        long startedAt = context.nanoTime().getAsLong();
        List<ValidationIssue> issues = new ArrayList<>();
        issues.add(new ValidationIssue(
                ValidationSeverity.INFO,
                "LOOT_CATEGORY_SCOPE_EXPANDED",
                context.requestedCategories().iterator().next(),
                ID,
                null,
                null,
                "Requested loot category expanded to predicates, item_modifiers and loot",
                null,
                null,
                new ValidationDetails("loot_bundle", null, null, null, null)
        ));

        LootResourceView initial = loader.load(context, startedAt, issues);
        Map<ResourceLocation, PreparedPredicate> predicates = new LinkedHashMap<>();
        Map<ResourceLocation, PreparedItemModifier> modifiers = new LinkedHashMap<>();
        Map<ResourceLocation, PreparedLootTable> tables = new LinkedHashMap<>();
        Map<LootDataId<?>, Object> candidate = new HashMap<>();
        List<LootDependency> dependencies = new ArrayList<>();
        Set<LootDependencyGraph.Node> nodes = new LinkedHashSet<>();

        parsePredicates(initial, predicates, candidate, dependencies, nodes, issues);
        parseModifiers(initial, modifiers, candidate, dependencies, nodes, issues);
        parseTables(initial, tables, candidate, dependencies, nodes, issues);
        candidate.put(LootDataManager.EMPTY_LOOT_TABLE_KEY, LootTable.EMPTY);

        if (dependencies.size() > context.maxDependencyEdges()) {
            throw new LootPreparationException(
                    "LOOT_LIMIT_EXCEEDED",
                    "Loot dependency edges " + dependencies.size()
                            + " exceed configured limit " + context.maxDependencyEdges()
            );
        }
        LootDependencyGraph graph = new LootDependencyGraph(nodes, dependencies);
        validateCandidate(candidate, initial, issues);
        addGraphDiagnostics(graph, initial, issues);
        addExternalDiagnostics(initial, issues);

        LootResourceLoader.checkDeadline(context, startedAt);
        LootResourceView verification = loader.load(context, startedAt, new ArrayList<>());
        if (!initial.stackFingerprints().equals(verification.stackFingerprints())) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.BLOCKER,
                    "LOOT_RESOURCE_CHANGED_DURING_PREPARATION",
                    ReloadCategory.LOOT,
                    ID,
                    null,
                    null,
                    "Loot resources changed while the candidate was being prepared",
                    null,
                    null,
                    new ValidationDetails("loot_bundle", null, null, null, null)
            ));
        }

        return new PreparedLootData(
                context.idSupplier().get(),
                Instant.now(context.clock()),
                initial.snapshot(),
                context.requestedCategories(),
                predicates,
                modifiers,
                tables,
                graph,
                deltaCalculator.between(
                        context.activeReference() == null
                                ? initial.snapshot() : context.activeReference(),
                        initial.snapshot(),
                        initial.packStacks()
                ),
                new ValidationReport(issues)
        );
    }

    private void parsePredicates(
            LootResourceView view,
            Map<ResourceLocation, PreparedPredicate> result,
            Map<LootDataId<?>, Object> candidate,
            List<LootDependency> dependencies,
            Set<LootDependencyGraph.Node> nodes,
            List<ValidationIssue> issues
    ) {
        for (LootResourceView.Source source : view.sources()
                .getOrDefault(LootDataKind.PREDICATE, Map.of()).values()) {
            try {
                LootItemCondition value;
                if (source.json().isJsonArray()) {
                    LootItemCondition[] array = LootDataType.PREDICATE.parser()
                            .fromJson(source.json(), LootItemCondition[].class);
                    value = LootDataManager.createComposite(array);
                } else {
                    value = LootDataType.PREDICATE.parser()
                            .fromJson(source.json(), LootItemCondition.class);
                }
                if (value == null) throw new IllegalArgumentException("Predicate deserialized to null");
                result.put(source.id(), new PreparedPredicate(source.id(), source.descriptor(), value));
                candidate.put(new LootDataId<>(LootDataType.PREDICATE, source.id()), value);
                addGraph(source, dependencies, nodes);
            } catch (RuntimeException exception) {
                issues.add(deserializationIssue(source, exception));
            }
        }
    }

    private void parseModifiers(
            LootResourceView view,
            Map<ResourceLocation, PreparedItemModifier> result,
            Map<LootDataId<?>, Object> candidate,
            List<LootDependency> dependencies,
            Set<LootDependencyGraph.Node> nodes,
            List<ValidationIssue> issues
    ) {
        for (LootResourceView.Source source : view.sources()
                .getOrDefault(LootDataKind.ITEM_MODIFIER, Map.of()).values()) {
            try {
                LootItemFunction value;
                if (source.json().isJsonArray()) {
                    LootItemFunction[] array = LootDataType.MODIFIER.parser()
                            .fromJson(source.json(), LootItemFunction[].class);
                    value = LootDataManager.createComposite(array);
                } else {
                    value = LootDataType.MODIFIER.parser()
                            .fromJson(source.json(), LootItemFunction.class);
                }
                if (value == null) throw new IllegalArgumentException("Item modifier deserialized to null");
                result.put(source.id(), new PreparedItemModifier(source.id(), source.descriptor(), value));
                candidate.put(new LootDataId<>(LootDataType.MODIFIER, source.id()), value);
                addGraph(source, dependencies, nodes);
            } catch (RuntimeException exception) {
                issues.add(deserializationIssue(source, exception));
            }
        }
    }

    private void parseTables(
            LootResourceView view,
            Map<ResourceLocation, PreparedLootTable> result,
            Map<LootDataId<?>, Object> candidate,
            List<LootDependency> dependencies,
            Set<LootDependencyGraph.Node> nodes,
            List<ValidationIssue> issues
    ) {
        for (LootResourceView.Source source : view.sources()
                .getOrDefault(LootDataKind.LOOT_TABLE, Map.of()).values()) {
            try {
                prevalidateTable(source);
                LootTable value = ForgeHooks.loadLootTable(
                        LootDataType.TABLE.parser(),
                        source.id(),
                        source.json(),
                        !source.winner().isBuiltin()
                );
                if (value == null) throw new IllegalArgumentException("Loot table deserialized to null");
                result.put(source.id(), new PreparedLootTable(source.id(), source.descriptor(), value));
                candidate.put(new LootDataId<>(LootDataType.TABLE, source.id()), value);
                addGraph(source, dependencies, nodes);
            } catch (RuntimeException exception) {
                issues.add(deserializationIssue(source, exception));
            }
        }
    }

    private static void prevalidateTable(LootResourceView.Source source) {
        if (!source.json().isJsonObject()) return;
        JsonElement random = source.json().getAsJsonObject().get("random_sequence");
        if (random != null && (!random.isJsonPrimitive()
                || ResourceLocation.tryParse(random.getAsString()) == null)) {
            throw new IllegalArgumentException("Invalid random_sequence resource location");
        }
    }

    private void addGraph(
            LootResourceView.Source source,
            List<LootDependency> dependencies,
            Set<LootDependencyGraph.Node> nodes
    ) {
        nodes.add(new LootDependencyGraph.Node(source.kind(), source.id()));
        dependencies.addAll(graphExtractor.extract(source.kind(), source.id(), source.json()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void validateCandidate(
            Map<LootDataId<?>, Object> candidate,
            LootResourceView view,
            List<ValidationIssue> issues
    ) {
        LootDataResolver resolver = new LootDataResolver() {
            @Override
            @Nullable
            public <T> T getElement(LootDataId<T> id) {
                return (T) candidate.get(id);
            }
        };
        net.minecraft.world.level.storage.loot.ValidationContext validation =
                new net.minecraft.world.level.storage.loot.ValidationContext(
                        LootContextParamSets.ALL_PARAMS, resolver
                );
        candidate.forEach((id, value) -> ((LootDataType) id.type())
                .runValidation(validation, (LootDataId) id, value));
        for (Map.Entry<String, String> problem : validation.getProblems().entries()) {
            ResolvedProblem resolved = resolveProblem(problem.getKey(), view);
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    validationCode(problem.getValue()),
                    resolved.kind() == null ? ReloadCategory.LOOT : resolved.kind().category(),
                    ID,
                    resolved.id(),
                    resolved.descriptor() == null ? null : resolved.descriptor().sourcePack(),
                    problem.getValue(),
                    resolved.descriptor() == null ? null : new SourceLocation(
                            resolved.descriptor().location(), 1, null, null, problem.getKey()
                    ),
                    null,
                    new ValidationDetails(
                            resolved.kind() == null ? null : resolved.kind().name(),
                            resolved.descriptor() == null ? null : resolved.descriptor().location(),
                            problem.getKey(),
                            problem.getKey(),
                            "LootDataType.runValidation"
                    )
            ));
        }
    }

    private static ResolvedProblem resolveProblem(String path, LootResourceView view) {
        Matcher matcher = ROOT.matcher(path);
        if (!matcher.find()) return new ResolvedProblem(null, null, null);
        LootDataKind kind = switch (matcher.group(1)) {
            case "predicates" -> LootDataKind.PREDICATE;
            case "item_modifiers" -> LootDataKind.ITEM_MODIFIER;
            default -> LootDataKind.LOOT_TABLE;
        };
        ResourceLocation id = ResourceLocation.tryParse(matcher.group(2));
        LootResourceView.Source source =
                id == null ? null : view.sources().getOrDefault(kind, Map.of()).get(id);
        return new ResolvedProblem(kind, id, source == null ? null : source.descriptor());
    }

    private static String validationCode(String message) {
        if (message.contains("Unknown condition table")) return "PREDICATE_REFERENCE_MISSING";
        if (message.contains("Unknown function table")) return "ITEM_MODIFIER_REFERENCE_MISSING";
        if (message.contains("Unknown loot table")) return "LOOT_TABLE_REFERENCE_MISSING";
        if (message.contains("recursively called")) return "LOOT_RECURSIVE_REFERENCE";
        if (message.contains("not provided in this context")) return "LOOT_CONTEXT_INCOMPATIBLE";
        return "LOOT_VALIDATION_ERROR";
    }

    private static void addGraphDiagnostics(
            LootDependencyGraph graph,
            LootResourceView view,
            List<ValidationIssue> issues
    ) {
        for (LootDependency missing : graph.missingReferences()) {
            LootResourceView.Source source =
                    view.sources().getOrDefault(missing.sourceKind(), Map.of()).get(missing.source());
            String code = switch (missing.type()) {
                case PREDICATE_REFERENCE -> "PREDICATE_REFERENCE_MISSING";
                case ITEM_MODIFIER_REFERENCE -> "ITEM_MODIFIER_REFERENCE_MISSING";
                default -> "LOOT_TABLE_REFERENCE_MISSING";
            };
            issues.add(issue(
                    ValidationSeverity.ERROR,
                    code,
                    missing.sourceKind(),
                    missing.source(),
                    source == null ? null : source.descriptor(),
                    missing.jsonPath(),
                    "Missing " + missing.targetKind() + " reference " + missing.target(),
                    null
            ));
        }
    }

    private static void addExternalDiagnostics(
            LootResourceView view,
            List<ValidationIssue> issues
    ) {
        if (view.hasGlobalLootModifiers()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "GLM_NOT_INCLUDED",
                    ReloadCategory.LOOT,
                    ID,
                    null,
                    null,
                    "Forge Global Loot Modifiers use a separate provider and are not included",
                    null,
                    null,
                    new ValidationDetails("global_loot_modifier", null, null, null, "Forge Codec")
            ));
        }
        view.sources().getOrDefault(LootDataKind.LOOT_TABLE, Map.of()).values().stream()
                .filter(source -> source.id().getNamespace().equals("silentgear")
                        && source.id().getPath().startsWith("inject/"))
                .forEach(source -> issues.add(issue(
                        ValidationSeverity.BLOCKER,
                        "LOOT_EXTERNAL_PROVIDER_UNSUPPORTED",
                        LootDataKind.LOOT_TABLE,
                        source.id(),
                        source.descriptor(),
                        "$",
                        "Silent Gear injection convention depends on an external LootTableLoadEvent contract",
                        null
                )));
    }

    private static ValidationIssue deserializationIssue(
            LootResourceView.Source source,
            RuntimeException exception
    ) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
        String code = message.contains("random_sequence")
                ? "LOOT_RANDOM_SEQUENCE_INVALID" : "LOOT_DESERIALIZATION_ERROR";
        if (message.contains("Unknown type")) {
            code = switch (source.kind()) {
                case PREDICATE -> "LOOT_UNKNOWN_CONDITION_TYPE";
                case ITEM_MODIFIER -> "LOOT_UNKNOWN_FUNCTION_TYPE";
                case LOOT_TABLE -> "LOOT_UNKNOWN_ENTRY_TYPE";
            };
        }
        return issue(
                ValidationSeverity.ERROR,
                code,
                source.kind(),
                source.id(),
                source.descriptor(),
                "$",
                message,
                exception
        );
    }

    static ValidationIssue issue(
            ValidationSeverity severity,
            String code,
            LootDataKind kind,
            @Nullable ResourceLocation resource,
            @Nullable ResourceDescriptor descriptor,
            @Nullable String jsonPath,
            String message,
            @Nullable Throwable cause
    ) {
        return new ValidationIssue(
                severity,
                code,
                kind.category(),
                ID,
                resource,
                descriptor == null ? null : descriptor.sourcePack(),
                message,
                descriptor == null ? null : new SourceLocation(
                        descriptor.location(), 1, null, null, jsonPath == null ? "$" : jsonPath
                ),
                cause == null ? null : cause.toString(),
                new ValidationDetails(
                        kind.name(),
                        descriptor == null ? null : descriptor.location(),
                        jsonPath,
                        null,
                        switch (kind) {
                            case PREDICATE -> "LootDataType.PREDICATE parser";
                            case ITEM_MODIFIER -> "LootDataType.MODIFIER parser";
                            case LOOT_TABLE -> "LootDataType.TABLE Forge parser";
                        }
                )
        );
    }

    private record ResolvedProblem(
            @Nullable LootDataKind kind,
            @Nullable ResourceLocation id,
            @Nullable ResourceDescriptor descriptor
    ) {
    }
}
