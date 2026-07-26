package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record ActiveTagSnapshot(TagState state, List<ResourceLocation> members) {
    public ActiveTagSnapshot { members = List.copyOf(members); }
}
