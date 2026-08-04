package com.gabriel0liv.partialreload.glm;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GlobalLootModifierStack {
    private GlobalLootModifierStack() {}

    public record Layer(String pack, boolean replace, List<ResourceLocation> entries) {
        public Layer { entries = List.copyOf(entries); }
    }

    public record Result(List<ResourceLocation> orderedIds, Map<ResourceLocation, String> sourcePacks) {
        public Result {
            orderedIds = List.copyOf(orderedIds);
            sourcePacks = Map.copyOf(sourcePacks);
        }
    }

    public static Result merge(List<Layer> layers) {
        List<ResourceLocation> ordered = new ArrayList<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        for (Layer layer : layers) {
            if (layer.replace()) { ordered.clear(); sources.clear(); }
            for (ResourceLocation id : layer.entries()) {
                ordered.remove(id);
                ordered.add(id);
                sources.remove(id);
                sources.put(id, layer.pack());
            }
        }
        LinkedHashMap<ResourceLocation, String> finalSources = new LinkedHashMap<>();
        ordered.forEach(id -> finalSources.put(id, sources.get(id)));
        return new Result(ordered, finalSources);
    }
}
