package com.gabriel0liv.partialreload.network.protocol;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ClientCapability {
    HANDSHAKE_V1(1);

    private static final Map<Integer, ClientCapability> BY_ID =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    ClientCapability::wireId, Function.identity()));

    private final int wireId;

    ClientCapability(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static ClientCapability fromWireId(int id) {
        return BY_ID.get(id);
    }
}
