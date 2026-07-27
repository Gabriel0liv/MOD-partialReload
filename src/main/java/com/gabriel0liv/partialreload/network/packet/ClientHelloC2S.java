package com.gabriel0liv.partialreload.network.packet;

import java.util.UUID;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;

import net.minecraft.network.FriendlyByteBuf;

public record ClientHelloC2S(UUID challenge, int protocolVersion,
        ClientCapabilities capabilities) {
    public ClientHelloC2S {
        if (challenge == null || capabilities == null) {
            throw new IllegalArgumentException("CLIENT_HANDSHAKE_PACKET_INVALID");
        }
    }

    public static void encode(ClientHelloC2S packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.challenge);
        buffer.writeVarInt(packet.protocolVersion);
        packet.capabilities.write(buffer);
    }

    public static ClientHelloC2S decode(FriendlyByteBuf buffer) {
        return new ClientHelloC2S(buffer.readUUID(), buffer.readVarInt(),
                ClientCapabilities.read(buffer));
    }
}
