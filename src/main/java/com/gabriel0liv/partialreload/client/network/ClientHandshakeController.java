package com.gabriel0liv.partialreload.client.network;

import java.util.UUID;

import com.gabriel0liv.partialreload.network.PartialReloadNetwork;
import com.gabriel0liv.partialreload.network.packet.ClientHelloC2S;
import com.gabriel0liv.partialreload.network.packet.HandshakeAcceptedS2C;
import com.gabriel0liv.partialreload.network.packet.ServerHelloS2C;
import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

public final class ClientHandshakeController {
    private static volatile ClientHandshakeState state = ClientHandshakeState.ABSENT;
    private static volatile UUID pendingChallenge;
    private static volatile int pendingProtocol;

    private ClientHandshakeController() {
    }

    public static void handle(ServerHelloS2C hello) {
        if (hello.protocolVersion() != ClientSyncProtocol.PROTOCOL_VERSION
                || !hello.requiredCapabilities().contains(ClientCapability.HANDSHAKE_V1)) {
            state = ClientHandshakeState.INCOMPATIBLE;
            return;
        }
        pendingChallenge = hello.challenge();
        pendingProtocol = hello.protocolVersion();
        state = ClientHandshakeState.PENDING;
        PartialReloadNetwork.sendToServer(new ClientHelloC2S(hello.challenge(),
                hello.protocolVersion(), ClientCapabilities.of(ClientCapability.HANDSHAKE_V1)));
    }

    public static void handle(HandshakeAcceptedS2C accepted) {
        if (state != ClientHandshakeState.PENDING
                || !accepted.challenge().equals(pendingChallenge)
                || accepted.protocolVersion() != pendingProtocol
                || !accepted.acceptedCapabilities().contains(ClientCapability.HANDSHAKE_V1)) {
            state = ClientHandshakeState.INCOMPATIBLE;
            return;
        }
        state = ClientHandshakeState.COMPATIBLE;
    }

    public static ClientHandshakeState state() {
        return state;
    }

    public static void reset() {
        pendingChallenge = null;
        pendingProtocol = 0;
        state = ClientHandshakeState.ABSENT;
    }
}
