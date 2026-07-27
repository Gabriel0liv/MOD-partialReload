package com.gabriel0liv.partialreload.network.protocol;

import java.util.UUID;

public record ClientHandshakeSession(
        UUID playerId,
        int connectionIdentity,
        UUID challenge,
        int protocolVersion,
        ClientCapabilities capabilities,
        ClientHandshakeState state,
        long createdTick,
        long deadlineTick,
        Long completedTick,
        String errorCode
) {
    public ClientHandshakeSession {
        if (playerId == null || challenge == null || capabilities == null || state == null) {
            throw new IllegalArgumentException("CLIENT_HANDSHAKE_SESSION_INVALID");
        }
    }
}
