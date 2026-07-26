package com.gabriel0liv.partialreload.core;

import net.minecraft.server.MinecraftServer;

@FunctionalInterface
public interface ConnectedPlayerProbe {
    int playerCount(MinecraftServer server);

    ConnectedPlayerProbe DEFAULT = server -> server.getPlayerList().getPlayerCount();
}
