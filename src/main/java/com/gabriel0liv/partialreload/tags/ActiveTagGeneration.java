package com.gabriel0liv.partialreload.tags;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.RegistryAccess;
import java.time.Instant;
import java.util.*;

/** Complete server-side tag bindings retained for one rollback. */
public final class ActiveTagGeneration {
    private final UUID generationId; private final Instant capturedAt;
    private final Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries;
    private final Map<ResourceKey<? extends Registry<?>>, ActiveRegistryTagSnapshot> snapshots;
    public ActiveTagGeneration(UUID id, Instant at, Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries) {
        this(id, at, registries, null);
    }
    public ActiveTagGeneration(UUID id, Instant at, Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries, RegistryAccess access) {
        this.generationId=id; this.capturedAt=at;
        Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> copy=new LinkedHashMap<>();
        registries.forEach((k,v)->copy.put(k, v.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e->List.copyOf(e.getValue())))));
        this.registries=Map.copyOf(copy);
        Map<ResourceKey<? extends Registry<?>>, ActiveRegistryTagSnapshot> structural = new LinkedHashMap<>();
        for (var entry : copy.entrySet()) {
            Map<ResourceLocation, ActiveTagSnapshot> tags = new LinkedHashMap<>();
            for (var tag : entry.getValue().entrySet()) {
                List<ResourceLocation> members = tag.getValue().stream().flatMap(h -> h.unwrapKey().stream().map(k -> k.location())).sorted().toList();
                tags.put(tag.getKey().location(), new ActiveTagSnapshot(members.isEmpty() ? TagState.EMPTY : TagState.RESOLVED, members));
            }
            int identity = 0;
            if (access != null) identity = System.identityHashCode(access.registryOrThrow((ResourceKey) entry.getKey()));
            structural.put(entry.getKey(), new ActiveRegistryTagSnapshot(entry.getKey(), identity, tags));
        }
        this.snapshots = Map.copyOf(structural);
    }
    public UUID generationId(){return generationId;} public Instant capturedAt(){return capturedAt;} public Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries(){return registries;} public Map<ResourceKey<? extends Registry<?>>, ActiveRegistryTagSnapshot> snapshots(){return snapshots;}
}
