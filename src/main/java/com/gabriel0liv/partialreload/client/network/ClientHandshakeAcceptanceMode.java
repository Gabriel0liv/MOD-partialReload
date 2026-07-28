package com.gabriel0liv.partialreload.client.network;

import net.minecraftforge.fml.loading.FMLEnvironment;

/** A narrowly scoped userdev seam for testing a client that remains silent. */
public enum ClientHandshakeAcceptanceMode {
    NORMAL,
    SILENT;

    public static ClientHandshakeAcceptanceMode current() {
        String raw = System.getProperty("partialreload.handshake.acceptance.mode", "NORMAL");
        ClientHandshakeAcceptanceMode mode;
        try {
            mode = valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            mode = NORMAL;
        }
        if (FMLEnvironment.production && mode != NORMAL) {
            throw new IllegalStateException(
                    "CLIENT_HANDSHAKE_ACCEPTANCE_MODE_NOT_AVAILABLE_IN_PRODUCTION");
        }
        return mode;
    }
}
