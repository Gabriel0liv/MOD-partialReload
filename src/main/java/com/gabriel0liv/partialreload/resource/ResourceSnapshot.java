package com.gabriel0liv.partialreload.resource;

import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ResourceSnapshot(Instant scannedAt, Map<ResourceLocation, ResourceDescriptor> resources) {
    public ResourceSnapshot {
        Objects.requireNonNull(scannedAt, "scannedAt");
        resources = Map.copyOf(Objects.requireNonNull(resources, "resources"));
        resources.forEach((location, descriptor) -> {
            if (!location.equals(descriptor.location())) {
                throw new IllegalArgumentException("Snapshot key must match descriptor location: " + location);
            }
        });
    }

    public Set<String> namespaces() {
        return resources.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .collect(Collectors.toUnmodifiableSet());
    }
}
