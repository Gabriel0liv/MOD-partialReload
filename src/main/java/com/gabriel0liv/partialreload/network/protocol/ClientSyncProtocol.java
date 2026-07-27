package com.gabriel0liv.partialreload.network.protocol;

import net.minecraft.resources.ResourceLocation;

public final class ClientSyncProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final ResourceLocation CHANNEL_ID =
            new ResourceLocation("partialreload", "client_sync");
    public static final int SERVER_HELLO_DISCRIMINATOR = 0;
    public static final int CLIENT_HELLO_DISCRIMINATOR = 1;
    public static final int HANDSHAKE_ACCEPTED_DISCRIMINATOR = 2;
    public static final int MAX_MOD_VERSION_LENGTH = 64;
    public static final int MAX_CAPABILITIES = 16;

    private ClientSyncProtocol() {
    }
}
