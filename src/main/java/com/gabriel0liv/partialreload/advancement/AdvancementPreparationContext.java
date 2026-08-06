package com.gabriel0liv.partialreload.advancement;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

public record AdvancementPreparationContext(ResourceManager resourceManager,
        MinecraftServer server, ResourceSnapshot snapshot, ResourceSnapshot activeReference,
        Duration timeout, int maxAdvancements, long maxJsonBytes, Clock clock,
        Supplier<UUID> idSupplier) {
    public AdvancementPreparationContext {
        if(timeout.isNegative()||timeout.isZero()||maxAdvancements<1||maxJsonBytes<1)
            throw new IllegalArgumentException("advancement preparation limits must be positive");
    }
}
