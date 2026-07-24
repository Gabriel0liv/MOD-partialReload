package com.gabriel0liv.partialreload.function;

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
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.validation.SourceLocation;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VanillaFunctionsProvider implements ReloadProvider {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("partialreload", "vanilla_functions");
    public static final ResourceLocation TICK_TAG =
            ResourceLocation.fromNamespaceAndPath("minecraft", "tick");
    public static final ResourceLocation LOAD_TAG =
            ResourceLocation.fromNamespaceAndPath("minecraft", "load");

    private final ResourceScanner scanner;
    private final FunctionResourceLoader loader = new FunctionResourceLoader();
    private final FunctionCompiler compiler = new FunctionCompiler();
    private final FunctionTagResolver tagResolver = new FunctionTagResolver();

    public VanillaFunctionsProvider(ResourceScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Set<ReloadCategory> categories() {
        return Set.of(ReloadCategory.FUNCTIONS);
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
        ReloadPlan plan = new ReloadPlanner(context.clock(), UUID::randomUUID)
                .createPlan(changeSet.forCategory(ReloadCategory.FUNCTIONS));
        return new ProviderPlan(
                ID,
                plan.categories(),
                plan.changedResources(),
                plan.risk(),
                plan.dependencies(),
                plan.warnings(),
                plan.blockers(),
                plan.supportStatus()
        );
    }

    public PreparedFunctions prepare(FunctionPreparationContext context)
            throws FunctionPreparationException {
        long startedAt = context.nanoTime().getAsLong();
        FunctionResourceView initial = loader.load(context, startedAt);
        FunctionCompiler.Result compilation =
                compiler.compile(context, startedAt, initial.functions());
        FunctionTagResolver.Result tags =
                tagResolver.resolve(initial.tags(), compilation.functions().keySet());

        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(compilation.issues());
        issues.addAll(tags.issues());
        validateDependencies(initial, compilation, tags.tags(), issues);

        Set<ResourceLocation> tick = tags.tags().getOrDefault(TICK_TAG, Set.of());
        Set<ResourceLocation> load = tags.tags().getOrDefault(LOAD_TAG, Set.of());
        List<FunctionDependency> dependencies = new ArrayList<>(compilation.dependencies());
        tick.forEach(id -> dependencies.add(new FunctionDependency(
                TICK_TAG, id, FunctionDependencyType.TICK_MEMBERSHIP, 0
        )));
        load.forEach(id -> dependencies.add(new FunctionDependency(
                LOAD_TAG, id, FunctionDependencyType.LOAD_MEMBERSHIP, 0
        )));

        FunctionSetDelta tickDelta =
                FunctionSetDelta.between(context.activeTickFunctions(), tick);
        FunctionSetDelta loadDelta =
                FunctionSetDelta.between(context.activeLoadFunctions(), load);
        if (tickDelta.changed()) {
            issues.add(issue(
                    ValidationSeverity.WARNING,
                    "TICK_FUNCTION_SET_CHANGED",
                    TICK_TAG,
                    null,
                    "Tick function set changed: +" + tickDelta.added().size()
                            + " -" + tickDelta.removed().size(),
                    null,
                    null
            ));
        }
        if (loadDelta.changed()) {
            issues.add(issue(
                    ValidationSeverity.WARNING,
                    "LOAD_FUNCTION_SET_CHANGED",
                    LOAD_TAG,
                    null,
                    "Load function set changed. Future commit requires explicit load policy; default is DO_NOT_RUN.",
                    null,
                    null
            ));
        }

        FunctionDependencyGraph graph = new FunctionDependencyGraph(
                compilation.functions().keySet(),
                dependencies,
                tags.tags()
        );
        graph.cycles().forEach(cycle -> issues.add(issue(
                ValidationSeverity.WARNING,
                "FUNCTION_RECURSION_DETECTED",
                cycle.iterator().next(),
                null,
                "Function recursion cycle detected: " + cycle,
                null,
                null
        )));

        FunctionResourceLoader.checkDeadline(context, startedAt);
        FunctionResourceView verification = loader.load(context, startedAt);
        if (!initial.snapshot().resources().equals(verification.snapshot().resources())) {
            issues.add(issue(
                    ValidationSeverity.BLOCKER,
                    "RESOURCE_CHANGED_DURING_PREPARATION",
                    null,
                    null,
                    "Function resources changed while preparation was running",
                    null,
                    null
            ));
        }

        return new PreparedFunctions(
                context.idSupplier().get(),
                Instant.now(context.clock()),
                initial.snapshot(),
                compilation.functions(),
                tags.tags(),
                tick,
                load,
                tickDelta,
                loadDelta,
                graph,
                new ValidationReport(issues),
                context.dispatcher(),
                context.compilationPermissionLevel()
        );
    }

    public com.gabriel0liv.partialreload.resource.ResourceSnapshot captureSnapshot(
            FunctionPreparationContext context
    ) throws FunctionPreparationException {
        long startedAt = context.nanoTime().getAsLong();
        return loader.load(context, startedAt).snapshot();
    }

    private static void validateDependencies(
            FunctionResourceView view,
            FunctionCompiler.Result compilation,
            Map<ResourceLocation, Set<ResourceLocation>> tags,
            List<ValidationIssue> issues
    ) {
        Set<String> reported = new LinkedHashSet<>();
        for (FunctionDependency dependency : compilation.dependencies()) {
            boolean present = dependency.tagTarget()
                    ? tags.containsKey(dependency.target())
                    : compilation.functions().containsKey(dependency.target());
            if (present) continue;
            String key = dependency.source() + "|" + dependency.target()
                    + "|" + dependency.type() + "|" + dependency.line();
            if (!reported.add(key)) continue;
            FunctionResourceView.FunctionSource source = view.functions().get(dependency.source());
            String command = source != null && dependency.line() > 0
                    && dependency.line() <= source.lines().size()
                    ? source.lines().get(dependency.line() - 1).trim()
                    : "";
            String code = dependency.tagTarget()
                    ? "FUNCTION_TAG_REFERENCE_MISSING"
                    : "FUNCTION_REFERENCE_MISSING";
            issues.add(issue(
                    ValidationSeverity.ERROR,
                    code,
                    dependency.source(),
                    source == null ? null : source.packId(),
                    "Referenced " + (dependency.tagTarget() ? "function tag #" : "function ")
                            + dependency.target() + " does not exist",
                    source == null || dependency.line() == 0
                            ? null
                            : new SourceLocation(source.file(), dependency.line(), null, null, command),
                    null
            ));
        }
    }

    private static ValidationIssue issue(
            ValidationSeverity severity,
            String code,
            ResourceLocation resource,
            String pack,
            String message,
            SourceLocation location,
            String cause
    ) {
        return new ValidationIssue(
                severity,
                code,
                ReloadCategory.FUNCTIONS,
                ID,
                resource,
                pack,
                message,
                location,
                cause
        );
    }
}
