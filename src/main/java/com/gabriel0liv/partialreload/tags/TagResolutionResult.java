package com.gabriel0liv.partialreload.tags;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record TagResolutionResult(TagResolutionStatus status, List<ResourceLocation> members, String diagnostic) {
    public TagResolutionResult { members = List.copyOf(members); }
}
