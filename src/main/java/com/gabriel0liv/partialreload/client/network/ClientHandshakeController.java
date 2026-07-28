package com.gabriel0liv.partialreload.client.network;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.gabriel0liv.partialreload.network.PartialReloadNetwork;
import com.gabriel0liv.partialreload.network.packet.ClientHelloC2S;
import com.gabriel0liv.partialreload.network.packet.ClientPresenceC2S;
import com.gabriel0liv.partialreload.network.packet.HandshakeAcceptedS2C;
import com.gabriel0liv.partialreload.network.packet.ServerHelloS2C;
import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeAcceptanceTrace;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientHandshakeController {
    private static volatile ClientHandshakeState state = ClientHandshakeState.ABSENT;
    private static volatile UUID pendingChallenge;
    private static volatile int pendingProtocol;
    private static volatile UUID clientSessionNonce;

    private ClientHandshakeController() {
    }

    public static void registerClientListeners() {
        MinecraftForge.EVENT_BUS.addListener(ClientHandshakeController::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !PartialReloadNetwork.isRemotePresent(listener.getConnection())) {
            ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SKIPPED_REMOTE_ABSENT",
                    null, ClientSyncProtocol.PROTOCOL_VERSION, ClientCapabilities.empty());
            return;
        }
        clientSessionNonce = UUID.randomUUID();
        ClientCapabilities capabilities = ClientCapabilities.of(ClientCapability.HANDSHAKE_V1);
        PartialReloadNetwork.sendPresence(new ClientPresenceC2S(ClientSyncProtocol.PROTOCOL_VERSION,
                com.gabriel0liv.partialreload.PartialReloadMod.VERSION, capabilities, clientSessionNonce));
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SENT",
                clientSessionNonce, ClientSyncProtocol.PROTOCOL_VERSION, capabilities);
    }

    public static void handle(ServerHelloS2C hello) {
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED",
                hello.challenge(), hello.protocolVersion(), hello.requiredCapabilities());
        if (hello.protocolVersion() != ClientSyncProtocol.PROTOCOL_VERSION
                || !hello.requiredCapabilities().contains(ClientCapability.HANDSHAKE_V1)) {
            state = ClientHandshakeState.INCOMPATIBLE;
            return;
        }
        pendingChallenge = hello.challenge();
        pendingProtocol = hello.protocolVersion();
        state = ClientHandshakeState.PENDING;
        if (ClientHandshakeAcceptanceMode.current() == ClientHandshakeAcceptanceMode.SILENT) {
            return;
        }
        PartialReloadNetwork.sendToServer(new ClientHelloC2S(hello.challenge(),
                hello.protocolVersion(), ClientCapabilities.of(ClientCapability.HANDSHAKE_V1)));
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_HELLO_SENT",
                hello.challenge(), hello.protocolVersion(),
                ClientCapabilities.of(ClientCapability.HANDSHAKE_V1));
    }

    public static void handle(HandshakeAcceptedS2C accepted) {
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_ACCEPTED",
                accepted.challenge(), accepted.protocolVersion(), accepted.acceptedCapabilities());
        if (state != ClientHandshakeState.PENDING
                || !accepted.challenge().equals(pendingChallenge)
                || accepted.protocolVersion() != pendingProtocol
                || !accepted.acceptedCapabilities().contains(ClientCapability.HANDSHAKE_V1)) {
            state = ClientHandshakeState.INCOMPATIBLE;
            return;
        }
        state = ClientHandshakeState.COMPATIBLE;
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE",
                accepted.challenge(), accepted.protocolVersion(), accepted.acceptedCapabilities());
    }

    public static ClientHandshakeState state() {
        return state;
    }

    public static void reset() {
        pendingChallenge = null;
        pendingProtocol = 0;
        clientSessionNonce = null;
        state = ClientHandshakeState.ABSENT;
        ClientHandshakeAcceptanceTrace.client("CLIENT_HANDSHAKE_CLIENT_RESET", null, 0,
                ClientCapabilities.empty());
    }
}
