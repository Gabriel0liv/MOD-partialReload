package com.gabriel0liv.partialreload.joint;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

    private static final ForgeNamespacedWrapperAccess FORGE_ACCESS = ForgeNamespacedWrapperAccess.resolve();

    public enum Kind { FORGE_NAMESPACED_WRAPPER, VANILLA_MAPPED_REGISTRY, UNSUPPORTED }

    public record Compatibility(boolean compatible, Kind kind, String concreteClass, String diagnostic) {}

    public static Compatibility inspect(RegistryAccess access,
                                        ResourceKey<? extends Registry<?>> key) {
        Registry<?> registry = access.registryOrThrow((ResourceKey) key);
        if (FORGE_ACCESS.isForgeWrapper(registry)) {
            return new Compatibility(FORGE_ACCESS.supported(), Kind.FORGE_NAMESPACED_WRAPPER,
                    registry.getClass().getName(), FORGE_ACCESS.diagnostic());
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
        if (FORGE_ACCESS.isForgeWrapper(raw)) {
            replaceForgeExact((Registry) raw, (Map) target, key);
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

    private static <T> void replaceForgeExact(Registry<T> registry,
                                              Map<TagKey<T>, List<Holder<T>>> requested,
                                              ResourceKey<? extends Registry<?>> key) {
        if (!FORGE_ACCESS.supported()) {
            throw unsupported(registry, key, FORGE_ACCESS.diagnostic());
        }
        Map<TagKey<T>, HolderSet.Named<T>> current = FORGE_ACCESS.readTags(registry);
        replaceOnMap(registry, current, requested, map -> FORGE_ACCESS.installTags(registry, map),
                registry::getOrCreateTag, null);
    }

    private static <T> void replaceVanillaExact(MappedRegistry<T> registry,
                                                Map<TagKey<T>, List<Holder<T>>> requested) {
        Map<TagKey<T>, HolderSet.Named<T>> current = registry.tags;
        replaceOnMap(registry, current, requested, map -> registry.tags = map,
                registry::getOrCreateTag, null);
    }

    private static <T> void replaceOnMap(Registry<T> registry,
                                         Map<TagKey<T>, HolderSet.Named<T>> current,
                                         Map<TagKey<T>, List<Holder<T>>> requested,
                                         Consumer<IdentityHashMap<TagKey<T>, HolderSet.Named<T>>> install,
                                         Function<TagKey<T>, HolderSet.Named<T>> createNamed,
                                         Runnable resetBeforeCreate) {
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

        if (resetBeforeCreate != null) resetBeforeCreate.run();

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
                named = createNamed.apply(canonicalKey);
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

    private static final class ForgeNamespacedWrapperAccess {
        private static final String CLASS_NAME = "net.minecraftforge.registries.NamespacedWrapper";
        private final Class<?> wrapperClass;
        private final Field tagsField;
        private final String diagnostic;

        private ForgeNamespacedWrapperAccess(Class<?> wrapperClass, Field tagsField, String diagnostic) {
            this.wrapperClass = wrapperClass;
            this.tagsField = tagsField;
            this.diagnostic = diagnostic;
        }

        private static ForgeNamespacedWrapperAccess resolve() {
            try {
                Class<?> wrapper = Class.forName(CLASS_NAME, false,
                        MappedRegistryTagBridge.class.getClassLoader());
                Field tags = wrapper.getField("tags");
                String diagnostic = validate(wrapper, tags);
                if (diagnostic != null) {
                    return new ForgeNamespacedWrapperAccess(wrapper, tags, diagnostic);
                }
                return new ForgeNamespacedWrapperAccess(wrapper, tags,
                        "NamespacedWrapper.tags is public and Map-compatible");
            } catch (ReflectiveOperationException | LinkageError error) {
                return new ForgeNamespacedWrapperAccess(null, null,
                        "runtimeClass=" + CLASS_NAME + " field=tags cause="
                                + error.getClass().getSimpleName() + ":"
                                + Objects.toString(error.getMessage(), "-"));
            }
        }

        private static String validate(Class<?> wrapper, Field tags) {
            if (!Modifier.isPublic(wrapper.getModifiers())) {
                return "runtimeClass=" + wrapper.getName() + " classModifiers="
                        + Modifier.toString(wrapper.getModifiers()) + " field=tags cause=CLASS_NOT_PUBLIC";
            }
            if (!MappedRegistry.class.isAssignableFrom(wrapper)) {
                return "runtimeClass=" + wrapper.getName() + " field=tags cause=NOT_MAPPED_REGISTRY_SUBTYPE";
            }
            if (tags.getDeclaringClass() != wrapper) {
                return "runtimeClass=" + wrapper.getName() + " field=tags declaringClass="
                        + tags.getDeclaringClass().getName() + " cause=FIELD_DECLARED_ELSEWHERE";
            }
            if (!Modifier.isPublic(tags.getModifiers())) {
                return "runtimeClass=" + wrapper.getName() + " field=tags fieldModifiers="
                        + Modifier.toString(tags.getModifiers()) + " cause=FIELD_NOT_PUBLIC";
            }
            if (!Map.class.isAssignableFrom(tags.getType())) {
                return "runtimeClass=" + wrapper.getName() + " field=tags fieldType="
                        + tags.getType().getName() + " cause=FIELD_NOT_MAP";
            }
            return null;
        }

        private boolean supported() {
            return wrapperClass != null && tagsField != null && diagnostic.startsWith("NamespacedWrapper.tags");
        }

        private String diagnostic() {
            return diagnostic;
        }

        private boolean isForgeWrapper(Object registry) {
            return wrapperClass != null && wrapperClass.isInstance(registry);
        }

        @SuppressWarnings("unchecked")
        private <T> Map<TagKey<T>, HolderSet.Named<T>> readTags(Object registry) {
            try {
                Object value = tagsField.get(registry);
                if (!(value instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: runtime class="
                            + registry.getClass().getName() + " field=tags cause=FIELD_VALUE_NOT_MAP");
                }
                return (Map<TagKey<T>, HolderSet.Named<T>>) map;
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: runtime class="
                        + registry.getClass().getName() + " field=tags cause="
                        + error.getClass().getSimpleName() + ":" + Objects.toString(error.getMessage(), "-"), error);
            }
        }

        private void installTags(Object registry, IdentityHashMap<?, ?> map) {
            try {
                tagsField.set(registry, map);
            } catch (IllegalAccessException | IllegalArgumentException error) {
                throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: runtime class="
                        + registry.getClass().getName() + " field=tags cause="
                        + error.getClass().getSimpleName() + ":" + Objects.toString(error.getMessage(), "-"), error);
            }
        }
    }
}
