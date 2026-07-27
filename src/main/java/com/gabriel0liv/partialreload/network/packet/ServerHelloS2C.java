package com.gabriel0liv.partialreload.network.packet;

import java.util.UUID;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

import net.minecraft.network.FriendlyByteBuf;

public record ServerHelloS2C(int protocolVersion, UUID challenge, String serverModVersion,
        ClientCapabilities requiredCapabilities) {
    public ServerHelloS2C {
        if (challenge == null || serverModVersion == null
                || serverModVersion.length() > ClientSyncProtocol.MAX_MOD_VERSION_LENGTH
                || requiredCapabilities == null) {
            throw new IllegalArgumentException("CLIENT_HANDSHAKE_PACKET_INVALID");
        }
    }

    public static void encode(ServerHelloS2C packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.protocolVersion);
        buffer.writeUUID(packet.challenge);
        buffer.writeUtf(packet.serverModVersion, ClientSyncProtocol.MAX_MOD_VERSION_LENGTH);
        packet.requiredCapabilities.write(buffer);
    }

    public static ServerHelloS2C decode(FriendlyByteBuf buffer) {
        return new ServerHelloS2C(buffer.readVarInt(), buffer.readUUID(),
                buffer.readUtf(ClientSyncProtocol.MAX_MOD_VERSION_LENGTH),
                ClientCapabilities.read(buffer));
    }
}
