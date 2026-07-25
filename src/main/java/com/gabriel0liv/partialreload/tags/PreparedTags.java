package com.gabriel0liv.partialreload.tags;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PreparedTags implements PreparedReloadArtifact {
    private final UUID preparationId; private final Instant createdAt; private final ResourceSnapshot snapshot;
    private final Map<String, PreparedRegistryTags> registries; private final TagDependencyGraph graph; private final TagDelta delta;
    private final ValidationReport validation; private final int discoveredFiles, preparedTags, resolvedMembers; private final Set<String> supported, unsupported;
    public PreparedTags(UUID id, Instant createdAt, ResourceSnapshot snapshot, Map<String, PreparedRegistryTags> registries, TagDependencyGraph graph, TagDelta delta, ValidationReport validation, int discoveredFiles, int preparedTags, int resolvedMembers, Set<String> supported, Set<String> unsupported) {
        this.preparationId=id; this.createdAt=createdAt; this.snapshot=snapshot; this.registries=Map.copyOf(registries); this.graph=graph; this.delta=delta; this.validation=validation; this.discoveredFiles=discoveredFiles; this.preparedTags=preparedTags; this.resolvedMembers=resolvedMembers; this.supported=Set.copyOf(supported); this.unsupported=Set.copyOf(unsupported);
    }
    public UUID preparationId(){return preparationId;} public ReloadCategory category(){return ReloadCategory.TAGS;} public Instant createdAt(){return createdAt;} public ResourceSnapshot sourceSnapshot(){return snapshot;}
    public Map<String, PreparedRegistryTags> registries(){return registries;} public TagDependencyGraph dependencyGraph(){return graph;} public TagDelta delta(){return delta;} public ValidationReport validation(){return validation;}
    public int discoveredFiles(){return discoveredFiles;} public int preparedTags(){return preparedTags;} public int resolvedMembers(){return resolvedMembers;} public Set<String> supportedRegistries(){return supported;} public Set<String> unsupportedRegistries(){return unsupported;}
}
