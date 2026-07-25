package com.gabriel0liv.partialreload.tags;

import java.util.Map;

public record PreparedRegistryTags(String registryPath, Map<net.minecraft.resources.ResourceLocation, PreparedTag> tags,
                                   int resolvedElementCount) {
    public PreparedRegistryTags { tags = Map.copyOf(tags); }
}
