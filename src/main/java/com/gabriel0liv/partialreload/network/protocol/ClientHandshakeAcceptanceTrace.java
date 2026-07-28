package com.gabriel0liv.partialreload.network.protocol;

import java.util.UUID;

import com.gabriel0liv.partialreload.PartialReloadMod;

import net.minecraftforge.fml.loading.FMLEnvironment;

/** Userdev-only, opt-in trace consumed by the real handshake acceptance harness. */
public final class ClientHandshakeAcceptanceTrace {
    private static final String PROPERTY = "partialreload.handshake.acceptance";

    private ClientHandshakeAcceptanceTrace() {
    }

    public static boolean enabled() {
        return !FMLEnvironment.production && Boolean.getBoolean(PROPERTY);
    }

    public static void server(ClientHandshakeSession session) {
        if (!enabled()) {
            return;
        }
        emit(marker(session.state()), session.playerId(), session.connectionIdentity(),
                session.challenge(), session.protocolVersion(), session.capabilities(),
                session.errorCode());
    }

    public static void server(String marker, ClientHandshakeSession session) {
        if (!enabled()) {
            return;
        }
        emit(marker, session.playerId(), session.connectionIdentity(), session.challenge(),
                session.protocolVersion(), session.capabilities(), session.errorCode());
    }

    public static void client(String marker, UUID challenge, int protocol,
            ClientCapabilities capabilities) {
        if (!enabled()) {
            return;
        }
        emit(marker, null, 0, challenge, protocol, capabilities, null);
    }

    private static void emit(String marker, UUID player, int connection, UUID challenge,
            int protocol, ClientCapabilities capabilities, String error) {
        PartialReloadMod.LOGGER.info(
                "{} player={} connection={} challenge={} protocol={} capabilities={} error={}",
                marker, value(player), connection, value(challenge), protocol,
                capabilities == null ? "[]" : capabilities, value(error));
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String marker(ClientHandshakeState state) {
        return switch (state) {
            case DISCOVERING -> "CLIENT_HANDSHAKE_SERVER_DISCOVERING";
            case ABSENT -> "CLIENT_HANDSHAKE_SERVER_ABSENT";
            case PENDING -> "CLIENT_HANDSHAKE_SERVER_PENDING";
            case COMPATIBLE -> "CLIENT_HANDSHAKE_SERVER_COMPATIBLE";
            case INCOMPATIBLE -> "CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE";
            case TIMED_OUT -> "CLIENT_HANDSHAKE_SERVER_TIMED_OUT";
            case DISCONNECTED -> "CLIENT_HANDSHAKE_SERVER_DISCONNECTED";
        };
    }
}
