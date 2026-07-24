package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PreparedFunctions implements PreparedReloadArtifact {
    private final UUID preparationId;
    private final Instant createdAt;
    private final ResourceSnapshot sourceSnapshot;
    private final Map<ResourceLocation, PreparedFunction> functions;
    private final Map<ResourceLocation, Set<ResourceLocation>> functionTags;
    private final Set<ResourceLocation> tickFunctions;
    private final Set<ResourceLocation> loadFunctions;
    private final FunctionSetDelta tickDelta;
    private final FunctionSetDelta loadDelta;
    private final FunctionDependencyGraph dependencyGraph;
    private final ValidationReport validation;

    public PreparedFunctions(
            UUID preparationId,
            Instant createdAt,
            ResourceSnapshot sourceSnapshot,
            Map<ResourceLocation, PreparedFunction> functions,
            Map<ResourceLocation, Set<ResourceLocation>> functionTags,
            Set<ResourceLocation> tickFunctions,
            Set<ResourceLocation> loadFunctions,
            FunctionSetDelta tickDelta,
            FunctionSetDelta loadDelta,
            FunctionDependencyGraph dependencyGraph,
            ValidationReport validation
    ) {
        this.preparationId = Objects.requireNonNull(preparationId, "preparationId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        LinkedHashMap<ResourceLocation, Set<ResourceLocation>> tags = new LinkedHashMap<>();
        functionTags.forEach((id, values) ->
                tags.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
        this.functionTags = Collections.unmodifiableMap(tags);
        this.tickFunctions = Collections.unmodifiableSet(new LinkedHashSet<>(tickFunctions));
        this.loadFunctions = Collections.unmodifiableSet(new LinkedHashSet<>(loadFunctions));
        this.tickDelta = Objects.requireNonNull(tickDelta, "tickDelta");
        this.loadDelta = Objects.requireNonNull(loadDelta, "loadDelta");
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    @Override
    public UUID preparationId() {
        return preparationId;
    }

    @Override
    public ReloadCategory category() {
        return ReloadCategory.FUNCTIONS;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public ResourceSnapshot sourceSnapshot() {
        return sourceSnapshot;
    }

    public Map<ResourceLocation, PreparedFunction> functions() {
        return functions;
    }

    public Map<ResourceLocation, Set<ResourceLocation>> functionTags() {
        return functionTags;
    }

    public Set<ResourceLocation> tickFunctions() {
        return tickFunctions;
    }

    public Set<ResourceLocation> loadFunctions() {
        return loadFunctions;
    }

    public FunctionSetDelta tickDelta() {
        return tickDelta;
    }

    public FunctionSetDelta loadDelta() {
        return loadDelta;
    }

    public FunctionDependencyGraph dependencyGraph() {
        return dependencyGraph;
    }

    @Override
    public ValidationReport validation() {
        return validation;
    }
}
