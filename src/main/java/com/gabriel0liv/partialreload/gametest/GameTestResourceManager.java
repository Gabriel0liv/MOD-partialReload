package com.gabriel0liv.partialreload.gametest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * A deliberately small resource view used only by server-side GameTests.
 */
final class GameTestResourceManager implements ResourceManager {
    private final Map<ResourceLocation, byte[]> resources;
    private final PackResources pack = new PackResources() {
        @Override
        @Nullable
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        @Nullable
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            byte[] bytes = resources.get(location);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(
                PackType type,
                String namespace,
                String path,
                ResourceOutput output
        ) {
            resources.forEach((location, bytes) -> {
                if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path)) {
                    output.accept(location, () -> new ByteArrayInputStream(bytes));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return GameTestResourceManager.this.getNamespaces();
        }

        @Override
        public <T> T getMetadataSection(net.minecraft.server.packs.metadata.MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public String packId() {
            return "partialreload-gametest-invalid";
        }

        @Override
        public void close() {
        }
    };

    GameTestResourceManager(Map<ResourceLocation, String> resources) {
        this.resources = resources.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Override
    public Set<String> getNamespaces() {
        return resources.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Stream<PackResources> listPacks() {
        return Stream.of(pack);
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        byte[] bytes = resources.get(location);
        return bytes == null
                ? Optional.empty()
                : Optional.of(new Resource(pack, () -> new ByteArrayInputStream(bytes)));
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation location) {
        return getResource(location).map(List::of).orElseGet(List::of);
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        return resources.keySet().stream()
                .filter(location -> location.getPath().startsWith(path))
                .filter(filter)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        location -> location,
                        location -> getResource(location).orElseThrow()
                ));
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(
            String path,
            Predicate<ResourceLocation> filter
    ) {
        return listResources(path, filter).entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.of(entry.getValue())
                ));
    }
}
