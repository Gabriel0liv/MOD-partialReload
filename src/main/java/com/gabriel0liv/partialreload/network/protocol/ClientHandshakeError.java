package com.gabriel0liv.partialreload.network.protocol;

public enum ClientHandshakeError {
    PROTOCOL_MISMATCH("TAG_RECIPE_CLIENT_PROTOCOL_MISMATCH"),
    HANDSHAKE_INVALID("TAG_RECIPE_CLIENT_HANDSHAKE_INVALID"),
    CAPABILITY_MISSING("TAG_RECIPE_CLIENT_CAPABILITY_MISSING"),
    READY_TIMEOUT("TAG_RECIPE_CLIENT_READY_TIMEOUT");

    private final String code;

    ClientHandshakeError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
