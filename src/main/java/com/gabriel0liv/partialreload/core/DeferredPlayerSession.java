package com.gabriel0liv.partialreload.core;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public record DeferredPlayerSession(
        UUID playerId,
        String playerName,
        Runnable closeContainer,
        BooleanSupplier inventoryMenuActive,
        Consumer<Component> messageSink
) {
    public DeferredPlayerSession {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(closeContainer, "closeContainer");
        Objects.requireNonNull(inventoryMenuActive, "inventoryMenuActive");
        Objects.requireNonNull(messageSink, "messageSink");
    }
}
