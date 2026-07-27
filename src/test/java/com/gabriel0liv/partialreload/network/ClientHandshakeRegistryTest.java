package com.gabriel0liv.partialreload.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState;
import com.gabriel0liv.partialreload.network.server.ClientHandshakeRegistry;

class ClientHandshakeRegistryTest {
    private static final ClientCapabilities HANDSHAKE =
            ClientCapabilities.of(ClientCapability.HANDSHAKE_V1);

    @Test
    void challengeConnectionAndPlayerAreBound() {
        ClientHandshakeRegistry registry = new ClientHandshakeRegistry();
        UUID player = UUID.randomUUID();
        var pending = registry.begin(player, 7, 10, 30);
        var accepted = registry.accept(player, 7, pending.challenge(), 1, HANDSHAKE, 11);
        assertTrue(accepted.accepted());
        assertEquals(ClientHandshakeState.COMPATIBLE, accepted.session().state());

        assertTrue(registry.accept(player, 7, pending.challenge(), 1, HANDSHAKE, 12).accepted());

        var stale = registry.accept(player, 8, pending.challenge(), 1, HANDSHAKE, 12);
        assertFalse(stale.accepted());
        assertEquals(ClientHandshakeRegistry.HANDSHAKE_INVALID, stale.errorCode());
    }

    @Test
    void mismatchMissingCapabilityTimeoutAndLogoutAreTerminal() {
        ClientHandshakeRegistry registry = new ClientHandshakeRegistry();
        UUID player = UUID.randomUUID();
        var pending = registry.begin(player, 1, 0, 5);
        var mismatch = registry.accept(player, 1, pending.challenge(), 99,
                HANDSHAKE, 1);
        assertEquals(ClientHandshakeState.INCOMPATIBLE, mismatch.session().state());

        UUID timedPlayer = UUID.randomUUID();
        var timed = registry.begin(timedPlayer, 2, 0, 5);
        registry.tick(6);
        assertEquals(ClientHandshakeState.TIMED_OUT, registry.session(timedPlayer).orElseThrow().state());
        assertFalse(registry.accept(timedPlayer, 2, timed.challenge(), 1, HANDSHAKE, 7).accepted());

        registry.disconnect(player, 1);
        assertTrue(registry.session(player).isEmpty());
    }

    @Test
    void absentAndSnapshotsAreIsolated() {
        ClientHandshakeRegistry registry = new ClientHandshakeRegistry();
        UUID player = UUID.randomUUID();
        assertEquals(ClientHandshakeState.ABSENT,
                registry.markAbsent(player, 4, 2).state());
        assertEquals(1, registry.snapshot().size());
        assertThrowsUnsupported(registry);
    }

    @Test
    void staleChallengeCannotPoisonAReplacementSession() {
        ClientHandshakeRegistry registry = new ClientHandshakeRegistry();
        UUID player = UUID.randomUUID();
        var oldSession = registry.begin(player, 1, 0, 20);
        var newSession = registry.begin(player, 2, 5, 25);
        var stale = registry.accept(player, 1, oldSession.challenge(), 1, HANDSHAKE, 6);
        assertFalse(stale.accepted());
        assertEquals(ClientHandshakeState.PENDING, registry.session(player).orElseThrow().state());
        assertEquals(newSession.challenge(), registry.session(player).orElseThrow().challenge());
    }

    private static void assertThrowsUnsupported(ClientHandshakeRegistry registry) {
        try {
            registry.snapshot().clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("snapshot must be immutable");
    }
}
