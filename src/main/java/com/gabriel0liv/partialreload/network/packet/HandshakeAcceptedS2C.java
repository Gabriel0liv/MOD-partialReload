package com.gabriel0liv.partialreload.network.packet;

import java.util.UUID;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;

import net.minecraft.network.FriendlyByteBuf;

public record HandshakeAcceptedS2C(UUID challenge, int protocolVersion,
        ClientCapabilities acceptedCapabilities) {
    public HandshakeAcceptedS2C {
        if (challenge == null || acceptedCapabilities == null) {
            throw new IllegalArgumentException("CLIENT_HANDSHAKE_PACKET_INVALID");
        }
    }

    public static void encode(HandshakeAcceptedS2C packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.challenge);
        buffer.writeVarInt(packet.protocolVersion);
        packet.acceptedCapabilities.write(buffer);
    }

    public static HandshakeAcceptedS2C decode(FriendlyByteBuf buffer) {
        return new HandshakeAcceptedS2C(buffer.readUUID(), buffer.readVarInt(),
                ClientCapabilities.read(buffer));
    }
}
