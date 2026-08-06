package com.gabriel0liv.partialreload.advancement;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record AdvancementResourceStack(ResourceLocation id, String logicalPath,
        String winningPack, String winningHash, List<String> overridePacks,
        List<String> overrideHashes) {
    public AdvancementResourceStack {
        overridePacks = List.copyOf(overridePacks);
        overrideHashes = List.copyOf(overrideHashes);
    }
}
