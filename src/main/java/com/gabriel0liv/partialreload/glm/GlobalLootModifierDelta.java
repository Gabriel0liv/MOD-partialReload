package com.gabriel0liv.partialreload.glm;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

public record GlobalLootModifierDelta(
        List<ResourceLocation> added,
        List<ResourceLocation> removed,
        List<ResourceLocation> modified,
        List<ResourceLocation> moved,
        List<ResourceLocation> restoredFromLowerPack,
        List<ResourceLocation> unchanged
) {
    public GlobalLootModifierDelta {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        modified = List.copyOf(modified);
        moved = List.copyOf(moved);
        restoredFromLowerPack = List.copyOf(restoredFromLowerPack);
        unchanged = List.copyOf(unchanged);
    }

    public int changedCount() {
        return added.size() + removed.size() + modified.size() + moved.size()
                + restoredFromLowerPack.size();
    }

    static List<ResourceLocation> movedIds(List<ResourceLocation> previousOrder,
                                           List<ResourceLocation> candidateOrder,
                                           Set<ResourceLocation> contentUnchanged) {
        return candidateOrder.stream()
                .filter(contentUnchanged::contains)
                .filter(id -> previousOrder.indexOf(id) >= 0)
                .filter(id -> previousOrder.indexOf(id) != candidateOrder.indexOf(id))
                .toList();
    }
}
