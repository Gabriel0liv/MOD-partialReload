package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionLibrary;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class FunctionGeneration {
    private final UUID generationId;
    private final Instant createdAt;
    private final ResourceSnapshot snapshot;
    private final ServerFunctionLibrary library;
    private final Set<ResourceLocation> functionIds;
    private final Map<ResourceLocation, Set<ResourceLocation>> functionTags;
    private final Set<ResourceLocation> tickFunctions;
    private final Set<ResourceLocation> loadFunctions;

    public FunctionGeneration(
            UUID generationId,
            Instant createdAt,
            ResourceSnapshot snapshot,
            ServerFunctionLibrary library,
            Set<ResourceLocation> functionIds,
            Map<ResourceLocation, Set<ResourceLocation>> functionTags,
            Set<ResourceLocation> tickFunctions,
            Set<ResourceLocation> loadFunctions
    ) {
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.library = Objects.requireNonNull(library, "library");
        this.functionIds = Set.copyOf(functionIds);
        LinkedHashMap<ResourceLocation, Set<ResourceLocation>> copied = new LinkedHashMap<>();
        functionTags.forEach((id, members) ->
                copied.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(members))));
        this.functionTags = Collections.unmodifiableMap(copied);
        this.tickFunctions = Collections.unmodifiableSet(new LinkedHashSet<>(tickFunctions));
        this.loadFunctions = Collections.unmodifiableSet(new LinkedHashSet<>(loadFunctions));
    }

    public UUID generationId() { return generationId; }
    public Instant createdAt() { return createdAt; }
    public ResourceSnapshot snapshot() { return snapshot; }
    public ServerFunctionLibrary library() { return library; }
    public Set<ResourceLocation> functionIds() { return functionIds; }
    public Map<ResourceLocation, Set<ResourceLocation>> functionTags() { return functionTags; }
    public Set<ResourceLocation> tickFunctions() { return tickFunctions; }
    public Set<ResourceLocation> loadFunctions() { return loadFunctions; }
}
