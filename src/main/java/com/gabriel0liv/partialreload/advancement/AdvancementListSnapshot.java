package com.gabriel0liv.partialreload.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record AdvancementListSnapshot(Set<ResourceLocation> roots,
        Map<ResourceLocation, ResourceLocation> parents,
        Map<ResourceLocation, Set<ResourceLocation>> children) {
    public AdvancementListSnapshot {
        roots=Set.copyOf(roots); parents=Map.copyOf(parents);
        Map<ResourceLocation,Set<ResourceLocation>> copy=new LinkedHashMap<>();
        children.forEach((id, values)->copy.put(id, Set.copyOf(values)));
        children=Collections.unmodifiableMap(copy);
    }
    public static AdvancementListSnapshot from(Collection<Advancement> values) {
        Set<ResourceLocation> roots=new LinkedHashSet<>();
        Map<ResourceLocation,ResourceLocation> parents=new LinkedHashMap<>();
        Map<ResourceLocation,Set<ResourceLocation>> children=new LinkedHashMap<>();
        for (Advancement value: values) {
            if (value.getParent()==null) roots.add(value.getId());
            else parents.put(value.getId(), value.getParent().getId());
            Set<ResourceLocation> ids=new LinkedHashSet<>();
            value.getChildren().forEach(child->ids.add(child.getId()));
            children.put(value.getId(), ids);
        }
        return new AdvancementListSnapshot(roots, parents, children);
    }
}
