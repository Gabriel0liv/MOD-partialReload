package com.gabriel0liv.partialreload.core;

import net.minecraft.server.MinecraftServer;

import java.util.List;

@FunctionalInterface
public interface DeferredPlayerSessionProbe {
    List<DeferredPlayerSession> capture(MinecraftServer server);

    DeferredPlayerSessionProbe DEFAULT = server -> server.getPlayerList().getPlayers().stream()
            .map(player -> new DeferredPlayerSession(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    player::closeContainer,
                    () -> player.containerMenu == player.inventoryMenu,
                    player::sendSystemMessage))
            .toList();
}
