package com.gabriel0liv.partialreload.network.packet;

import java.util.UUID;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

import net.minecraft.network.FriendlyByteBuf;

/** First packet sent by a compatible client on each physical connection. */
public record ClientPresenceC2S(int protocolVersion, String clientModVersion,
        ClientCapabilities capabilities, UUID clientSessionNonce) {
    public ClientPresenceC2S {
        if (clientModVersion == null || clientModVersion.isEmpty()
                || clientModVersion.length() > ClientSyncProtocol.MAX_MOD_VERSION_LENGTH
                || clientModVersion.chars().anyMatch(Character::isISOControl)
                || capabilities == null || clientSessionNonce == null) {
            throw new IllegalArgumentException("CLIENT_HANDSHAKE_PRESENCE_INVALID");
        }
    }

    public static void encode(ClientPresenceC2S packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.protocolVersion());
        buffer.writeUtf(packet.clientModVersion(), ClientSyncProtocol.MAX_MOD_VERSION_LENGTH);
        packet.capabilities().write(buffer);
        buffer.writeUUID(packet.clientSessionNonce());
    }

    public static ClientPresenceC2S decode(FriendlyByteBuf buffer) {
        return new ClientPresenceC2S(buffer.readVarInt(),
                buffer.readUtf(ClientSyncProtocol.MAX_MOD_VERSION_LENGTH),
                ClientCapabilities.read(buffer), buffer.readUUID());
    }
}
