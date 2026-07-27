package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.server.MinecraftServer;

@FunctionalInterface
public interface TagRecipeCurrentResourceProbe {
    boolean matches(MinecraftServer server, ResourceSnapshot expected);
}
