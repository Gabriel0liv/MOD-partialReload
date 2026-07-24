package com.gabriel0liv.partialreload.function;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public record FunctionPreparationContext(
        ResourceManager resourceManager,
        CommandDispatcher<CommandSourceStack> dispatcher,
        int compilationPermissionLevel,
        Set<ResourceLocation> activeTickFunctions,
        Set<ResourceLocation> activeLoadFunctions,
        Duration timeout,
        int maxScannedResources,
        int maxFunctionCount,
        int maxFunctionLines,
        Clock clock,
        Supplier<UUID> idSupplier,
        LongSupplier nanoTime
) {
    public FunctionPreparationContext {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Objects.requireNonNull(dispatcher, "dispatcher");
        activeTickFunctions = Set.copyOf(activeTickFunctions);
        activeLoadFunctions = Set.copyOf(activeLoadFunctions);
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(idSupplier, "idSupplier");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (compilationPermissionLevel < 0 || compilationPermissionLevel > 4) {
            throw new IllegalArgumentException("compilationPermissionLevel must be between 0 and 4");
        }
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (maxScannedResources < 1 || maxFunctionCount < 1 || maxFunctionLines < 1) {
            throw new IllegalArgumentException("preparation limits must be positive");
        }
    }
}
