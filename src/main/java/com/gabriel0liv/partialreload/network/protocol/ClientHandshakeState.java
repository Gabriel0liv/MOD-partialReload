package com.gabriel0liv.partialreload.network.protocol;

public enum ClientHandshakeState {
    DISCOVERING,
    ABSENT,
    PENDING,
    COMPATIBLE,
    INCOMPATIBLE,
    TIMED_OUT,
    DISCONNECTED
}
