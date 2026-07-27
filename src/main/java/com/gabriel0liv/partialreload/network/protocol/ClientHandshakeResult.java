package com.gabriel0liv.partialreload.network.protocol;

public record ClientHandshakeResult(boolean accepted, ClientHandshakeSession session,
        String errorCode) {
}
