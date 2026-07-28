package com.gabriel0liv.partialreload.network.server;


import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.network.PartialReloadNetwork;
import com.gabriel0liv.partialreload.network.packet.ClientHelloC2S;
import com.gabriel0liv.partialreload.network.packet.ClientPresenceC2S;
import com.gabriel0liv.partialreload.network.packet.HandshakeAcceptedS2C;
import com.gabriel0liv.partialreload.network.packet.ServerHelloS2C;
import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeResult;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeSession;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeAcceptanceTrace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public final class ClientHandshakeServer {
    private final ClientHandshakeRegistry registry = new ClientHandshakeRegistry();

    public ClientHandshakeRegistry registry() {
        return registry;
    }

    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int identity = System.identityHashCode(player.connection.connection);
        long now = player.getServer().getTickCount();
        ClientHandshakeRegistry.DiscoveryResult discovery = registry.ensureDiscovery(player.getUUID(), identity, now,
                now + PartialReloadConfig.clientSyncPresenceTimeoutTicks());
        if (discovery.created()) {
            ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_DISCOVERING", discovery.session());
        }
    }

    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int identity = System.identityHashCode(player.connection.connection);
            registry.session(player.getUUID()).ifPresent(session -> {
                if (session.connectionIdentity() == identity) {
                    ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_DISCONNECTED",
                            new ClientHandshakeSession(session.playerId(), session.connectionIdentity(),
                                    session.challenge(), session.protocolVersion(), session.capabilities(),
                                    com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState.DISCONNECTED,
                                    session.createdTick(), session.deadlineTick(),
                                    Long.valueOf(player.getServer().getTickCount()), session.errorCode()));
                }
            });
            registry.disconnect(player.getUUID(), identity);
        }
    }

    public void tick(long currentTick) {
        for (ClientHandshakeSession session : registry.tick(currentTick)) {
            if (session.state() == com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState.ABSENT) {
                ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_ABSENT", session);
            } else {
                ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_TIMED_OUT", session);
            }
        }
    }

    public void clear() {
        registry.clear();
    }

    public static void handleClientHello(ClientHelloC2S message, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.getServer() == null) {
                return;
            }
            ClientHandshakeServer server = PartialReloadMod.networkServer();
            if (server == null) {
                return;
            }
            ClientHandshakeResult result = server.registry.accept(sender.getUUID(),
                    System.identityHashCode(sender.connection.connection), message.challenge(),
                    message.protocolVersion(), message.capabilities(),
                    sender.getServer().getTickCount());
            if (result.session() != null) {
                ClientHandshakeAcceptanceTrace.server(
                        result.accepted() ? "CLIENT_HANDSHAKE_SERVER_COMPATIBLE"
                                : "CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE",
                        result.session());
            }
            if (result.accepted()) {
                PartialReloadNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                        new HandshakeAcceptedS2C(message.challenge(), message.protocolVersion(),
                                message.capabilities()));
            }
        });
        context.setPacketHandled(true);
    }

    public static void handleClientPresence(ClientPresenceC2S message, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.getServer() == null) {
                return;
            }
            ClientHandshakeServer server = PartialReloadMod.networkServer();
            if (server == null) {
                return;
            }
            int identity = System.identityHashCode(sender.connection.connection);
            long now = sender.getServer().getTickCount();
            ClientHandshakeRegistry.DiscoveryResult discovery = server.registry.ensureDiscovery(sender.getUUID(),
                    identity, now, now + PartialReloadConfig.clientSyncPresenceTimeoutTicks());
            if (discovery.created()) {
                ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_DISCOVERING", discovery.session());
            }
            ClientHandshakeResult result = server.registry.acceptPresence(sender.getUUID(), identity,
                    message.clientSessionNonce(), message.protocolVersion(), message.capabilities(),
                    message.clientModVersion(), now,
                    now + PartialReloadConfig.clientSyncHandshakeTimeoutTicks());
            if (!result.accepted()) {
                if (result.session() != null) {
                    ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE", result.session());
                }
                return;
            }
            ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED", result.session());
            ClientHandshakeAcceptanceTrace.server("CLIENT_HANDSHAKE_SERVER_PENDING", result.session());
            PartialReloadNetwork.sendHello(sender, new ServerHelloS2C(
                    result.session().protocolVersion(), result.session().challenge(), PartialReloadMod.VERSION,
                    ClientCapabilities.of(ClientCapability.HANDSHAKE_V1)));
        });
        context.setPacketHandled(true);
    }
}
