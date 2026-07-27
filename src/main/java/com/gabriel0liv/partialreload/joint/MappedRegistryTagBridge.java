package com.gabriel0liv.partialreload.joint;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact tag replacement for the 1.20.1 MappedRegistry implementation.
 *
 * bindTags intentionally keeps named HolderSets that are absent from the
 * incoming map so vanilla can warn about missing datapack tags. That is not
 * suitable for transactional reloads: omitted keys must become MISSING.
 */
public final class MappedRegistryTagBridge {
    private MappedRegistryTagBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replaceExact(RegistryAccess access,
                                    ResourceKey<? extends Registry<?>> key,
                                    Map<TagKey<?>, List<Holder<?>>> target) {
        Registry<?> raw = access.registryOrThrow((ResourceKey) key);
        if (!(raw instanceof MappedRegistry<?> registry)) {
            throw new IllegalStateException("TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED: " + key.location());
        }
        Map<TagKey<?>, HolderSet.Named<?>> before = new IdentityHashMap<>();
        registry.tags.forEach((tag, named) -> before.put((TagKey<?>) tag, (HolderSet.Named<?>) named));

        // MappedRegistry.bindTags resets holder membership and binds the exact
        // target members, but leaves omitted named sets indexed. Remove those
        // keys after bind and clear the old object for external references.
        registry.bindTags((Map) target);
        Map<TagKey<?>, HolderSet.Named<?>> indexed = (Map) registry.tags;
        for (Map.Entry<TagKey<?>, HolderSet.Named<?>> entry : before.entrySet()) {
            if (!target.containsKey(entry.getKey())) {
                entry.getValue().bind(List.of());
                indexed.remove(entry.getKey());
            }
        }
        // The service performs the holder/member verification immediately
        // after this bridge returns. Avoid comparing TagKey object identity
        // here: registries may canonicalize keys while binding.
    }
}
