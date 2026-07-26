package com.gabriel0liv.partialreload.tags;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import java.time.Instant;
import java.util.*;

/** Complete server-side tag bindings retained for one rollback. */
public final class ActiveTagGeneration {
    private final UUID generationId; private final Instant capturedAt;
    private final Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries;
    public ActiveTagGeneration(UUID id, Instant at, Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries) {
        this.generationId=id; this.capturedAt=at;
        Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> copy=new LinkedHashMap<>();
        registries.forEach((k,v)->copy.put(k, v.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e->List.copyOf(e.getValue())))));
        this.registries=Map.copyOf(copy);
    }
    public UUID generationId(){return generationId;} public Instant capturedAt(){return capturedAt;} public Map<ResourceKey<? extends Registry<?>>, Map<TagKey<?>, List<Holder<?>>>> registries(){return registries;}
}
