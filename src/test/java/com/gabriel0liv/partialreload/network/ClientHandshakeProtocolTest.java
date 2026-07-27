package com.gabriel0liv.partialreload.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabriel0liv.partialreload.network.packet.ClientHelloC2S;
import com.gabriel0liv.partialreload.network.packet.HandshakeAcceptedS2C;
import com.gabriel0liv.partialreload.network.packet.ServerHelloS2C;
import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class ClientHandshakeProtocolTest {
    @Test
    void packetsRoundTripWithoutClientRuntime() {
        UUID challenge = UUID.randomUUID();
        ClientCapabilities capabilities = ClientCapabilities.of(ClientCapability.HANDSHAKE_V1);

        FriendlyByteBuf serverBuffer = new FriendlyByteBuf(Unpooled.buffer());
        ServerHelloS2C server = new ServerHelloS2C(1, challenge, "0.3.0", capabilities);
        ServerHelloS2C.encode(server, serverBuffer);
        assertEquals(server, ServerHelloS2C.decode(serverBuffer));

        FriendlyByteBuf clientBuffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientHelloC2S client = new ClientHelloC2S(challenge, 1, capabilities);
        ClientHelloC2S.encode(client, clientBuffer);
        assertEquals(client, ClientHelloC2S.decode(clientBuffer));

        FriendlyByteBuf acceptedBuffer = new FriendlyByteBuf(Unpooled.buffer());
        HandshakeAcceptedS2C accepted = new HandshakeAcceptedS2C(challenge, 1, capabilities);
        HandshakeAcceptedS2C.encode(accepted, acceptedBuffer);
        assertEquals(accepted, HandshakeAcceptedS2C.decode(acceptedBuffer));
    }

    @Test
    void capabilityCodecRejectsUnknownDuplicateAndOversizedValues() {
        FriendlyByteBuf unknown = new FriendlyByteBuf(Unpooled.buffer());
        unknown.writeVarInt(1);
        unknown.writeVarInt(999);
        assertThrows(IllegalArgumentException.class, () -> ClientCapabilities.read(unknown));

        FriendlyByteBuf duplicate = new FriendlyByteBuf(Unpooled.buffer());
        duplicate.writeVarInt(2);
        duplicate.writeVarInt(1);
        duplicate.writeVarInt(1);
        assertThrows(IllegalArgumentException.class, () -> ClientCapabilities.read(duplicate));

        FriendlyByteBuf tooMany = new FriendlyByteBuf(Unpooled.buffer());
        tooMany.writeVarInt(ClientSyncProtocol.MAX_CAPABILITIES + 1);
        assertThrows(IllegalArgumentException.class, () -> ClientCapabilities.read(tooMany));
    }

    @Test
    void modVersionLimitIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new ServerHelloS2C(1,
                UUID.randomUUID(), "x".repeat(ClientSyncProtocol.MAX_MOD_VERSION_LENGTH + 1),
                ClientCapabilities.empty()));
    }
}
