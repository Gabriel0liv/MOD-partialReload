package com.gabriel0liv.partialreload.joint;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.NamespacedWrapper;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Exact tag replacement for Forge 1.20.1 and the vanilla MappedRegistry.
 *
 * Forge's NamespacedWrapper shadows MappedRegistry.tags.  Accessing only the
 * superclass field leaves the public Forge lookup unchanged, so Forge and
 * vanilla paths are deliberately kept separate here.
 */
public final class MappedRegistryTagBridge {
    private MappedRegistryTagBridge() {}

    public enum Kind { FORGE_NAMESPACED_WRAPPER, VANILLA_MAPPED_REGISTRY, UNSUPPORTED }

    public record Compatibility(boolean compatible, Kind kind, String concreteClass, String diagnostic) {}

    public static Compatibility inspect(RegistryAccess access,
                                        ResourceKey<? extends Registry<?>> key) {
        Registry<?> registry = access.registryOrThrow((ResourceKey) key);
        if (registry instanceof NamespacedWrapper<?>) {
            return new Compatibility(true, Kind.FORGE_NAMESPACED_WRAPPER,
                    registry.getClass().getName(), "NamespacedWrapper.tags is accessible");
        }
        if (registry instanceof MappedRegistry<?>) {
            return new Compatibility(true, Kind.VANILLA_MAPPED_REGISTRY,
                    registry.getClass().getName(), "MappedRegistry.tags is accessible");
        }
        return new Compatibility(false, Kind.UNSUPPORTED,
                registry.getClass().getName(), "registry is neither a supported wrapper nor MappedRegistry");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replaceExact(RegistryAccess access,
                                    ResourceKey<? extends Registry<?>> key,
                                    Map<TagKey<?>, List<Holder<?>>> target) {
        Registry<?> raw = access.registryOrThrow((ResourceKey) key);
        if (raw instanceof NamespacedWrapper<?> forge) {
            replaceForgeExact((NamespacedWrapper) forge, (Map) target);
            return;
        }
        if (raw instanceof MappedRegistry<?> vanilla) {
            replaceVanillaExact((MappedRegistry) vanilla, (Map) target);
            return;
        }
        throw unsupported(raw, key, "unsupported registry implementation");
    }

    private static IllegalStateException unsupported(Registry<?> registry,
                                                      ResourceKey<? extends Registry<?>> key,
                                                      String reason) {
        return new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: registry="
                + key.location() + " class=" + registry.getClass().getName() + " reason=" + reason);
    }

    private static <T> void replaceForgeExact(NamespacedWrapper<T> registry,
                                              Map<TagKey<T>, List<Holder<T>>> requested) {
        Map<TagKey<T>, HolderSet.Named<T>> current = registry.tags;
        replaceOnMap(registry, current, requested, map -> registry.tags = map);
    }

    private static <T> void replaceVanillaExact(MappedRegistry<T> registry,
                                                Map<TagKey<T>, List<Holder<T>>> requested) {
        Map<TagKey<T>, HolderSet.Named<T>> current = registry.tags;
        replaceOnMap(registry, current, requested, map -> registry.tags = map);
    }

    private static <T> void replaceOnMap(Registry<T> registry,
                                         Map<TagKey<T>, HolderSet.Named<T>> current,
                                         Map<TagKey<T>, List<Holder<T>>> requested,
                                         Consumer<IdentityHashMap<TagKey<T>, HolderSet.Named<T>>> install) {
        Map<ResourceLocation, Map.Entry<TagKey<T>, HolderSet.Named<T>>> oldByLocation =
                current.entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey().location(), Function.identity(), (a, b) -> {
                            throw new IllegalStateException("duplicate logical tag location " + a.getKey().location());
                        }));

        Map<ResourceLocation, Map.Entry<TagKey<T>, List<Holder<T>>>> targetByLocation =
                requested.entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey().location(), Function.identity(), (a, b) -> {
                            throw new IllegalStateException("duplicate logical target tag location " + a.getKey().location());
                        }));

        oldByLocation.forEach((location, old) -> {
            if (!targetByLocation.containsKey(location)) {
                old.getValue().bind(List.of());
            }
        });

        IdentityHashMap<TagKey<T>, HolderSet.Named<T>> seed = new IdentityHashMap<>();
        IdentityHashMap<TagKey<T>, List<Holder<T>>> canonicalTarget = new IdentityHashMap<>();
        targetByLocation.forEach((location, requestedEntry) -> {
            TagKey<T> canonicalKey;
            HolderSet.Named<T> named;
            Map.Entry<TagKey<T>, HolderSet.Named<T>> old = oldByLocation.get(location);
            if (old != null) {
                canonicalKey = old.getKey();
                named = old.getValue();
            } else {
                canonicalKey = requestedEntry.getKey();
                named = registry instanceof NamespacedWrapper<T> forge
                        ? forge.getOrCreateTag(canonicalKey)
                        : ((MappedRegistry<T>) registry).getOrCreateTag(canonicalKey);
            }
            seed.put(canonicalKey, named);
            canonicalTarget.put(canonicalKey, requestedEntry.getValue());
        });

        install.accept(seed);
        registry.bindTags(canonicalTarget);

        // The public lookup is the contract; this also catches an AT pointing
        // at the wrong shadowed field before the transaction can continue.
        for (Map.Entry<TagKey<T>, List<Holder<T>>> entry : canonicalTarget.entrySet()) {
            HolderSet.Named<T> observed = registry.getTag(entry.getKey()).orElseThrow(() ->
                    new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: target tag missing "
                            + entry.getKey().location()));
            if (observed.size() != entry.getValue().size()) {
                throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: target size mismatch "
                        + entry.getKey().location());
            }
        }
        for (Map.Entry<ResourceLocation, Map.Entry<TagKey<T>, HolderSet.Named<T>>> old : oldByLocation.entrySet()) {
            if (!targetByLocation.containsKey(old.getKey()) && !registry.getTag(old.getValue().getKey()).isEmpty()) {
                throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: removed tag remains "
                        + old.getKey());
            }
            if (!targetByLocation.containsKey(old.getKey()) && old.getValue().getValue().size() != 0) {
                throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: stale named set "
                        + old.getKey());
            }
        }
    }
}
