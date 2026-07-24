package com.gabriel0liv.partialreload.function;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class InMemoryResourceManager implements ResourceManager {
    private final Map<ResourceLocation, List<Layer>> resources = new LinkedHashMap<>();
    private Runnable afterFirstStackListing;
    private int stackListings;

    InMemoryResourceManager put(String pack, String id, String content) {
        ResourceLocation location = ResourceLocation.parse(id);
        resources.computeIfAbsent(location, ignored -> new ArrayList<>())
                .add(new Layer(new MemoryPack(pack), content.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    InMemoryResourceManager replaceWinner(String id, String content) {
        ResourceLocation location = ResourceLocation.parse(id);
        List<Layer> layers = resources.get(location);
        Layer winner = layers.get(layers.size() - 1);
        layers.set(layers.size() - 1, new Layer(
                winner.pack(),
                content.getBytes(StandardCharsets.UTF_8)
        ));
        return this;
    }

    void mutateAfterFirstStackListing(Runnable mutation) {
        this.afterFirstStackListing = mutation;
    }

    @Override
    public Set<String> getNamespaces() {
        LinkedHashSet<String> namespaces = new LinkedHashSet<>();
        resources.keySet().forEach(id -> namespaces.add(id.getNamespace()));
        return Set.copyOf(namespaces);
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        List<Layer> layers = resources.get(location);
        return layers == null || layers.isEmpty()
                ? Optional.empty()
                : Optional.of(asResource(layers.get(layers.size() - 1)));
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation location) {
        return resources.getOrDefault(location, List.of()).stream()
                .map(InMemoryResourceManager::asResource)
                .toList();
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(
            String prefix,
            Predicate<ResourceLocation> predicate
    ) {
        Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
        resources.forEach((id, layers) -> {
            if (matches(id, prefix) && predicate.test(id) && !layers.isEmpty()) {
                result.put(id, asResource(layers.get(layers.size() - 1)));
            }
        });
        return result;
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(
            String prefix,
            Predicate<ResourceLocation> predicate
    ) {
        Map<ResourceLocation, List<Resource>> result = new LinkedHashMap<>();
        resources.forEach((id, layers) -> {
            if (matches(id, prefix) && predicate.test(id)) {
                result.put(id, layers.stream().map(InMemoryResourceManager::asResource).toList());
            }
        });
        stackListings++;
        if (stackListings == 1 && afterFirstStackListing != null) afterFirstStackListing.run();
        return result;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return resources.values().stream()
                .flatMap(List::stream)
                .map(layer -> (PackResources) layer.pack())
                .distinct();
    }

    private static boolean matches(ResourceLocation id, String prefix) {
        return id.getPath().equals(prefix) || id.getPath().startsWith(prefix + "/");
    }

    private static Resource asResource(Layer layer) {
        return new Resource(layer.pack(), () -> new ByteArrayInputStream(layer.bytes()));
    }

    private record Layer(MemoryPack pack, byte[] bytes) {
    }

    private record MemoryPack(String packId) implements PackResources {
        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of();
        }

        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
