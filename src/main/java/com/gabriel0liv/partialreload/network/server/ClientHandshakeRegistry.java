package com.gabriel0liv.partialreload.network.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.gabriel0liv.partialreload.network.protocol.ClientCapabilities;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeResult;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeSession;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeState;
import com.gabriel0liv.partialreload.network.protocol.ClientHandshakeError;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;

public final class ClientHandshakeRegistry {
    public static final String PROTOCOL_MISMATCH = ClientHandshakeError.PROTOCOL_MISMATCH.code();
    public static final String HANDSHAKE_INVALID = ClientHandshakeError.HANDSHAKE_INVALID.code();
    public static final String CAPABILITY_MISSING = ClientHandshakeError.CAPABILITY_MISSING.code();
    public static final String READY_TIMEOUT = ClientHandshakeError.READY_TIMEOUT.code();

    private final Map<UUID, ClientHandshakeSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> presenceNonces = new HashMap<>();

    public record DiscoveryResult(ClientHandshakeSession session, boolean created) {}

    public synchronized DiscoveryResult ensureDiscovery(UUID playerId, int connectionIdentity,
            long currentTick, long deadlineTick) {
        ClientHandshakeSession current = sessions.get(playerId);
        if (current != null && current.connectionIdentity() == connectionIdentity
                && current.state() != ClientHandshakeState.DISCONNECTED) {
            return new DiscoveryResult(current, false);
        }
        ClientHandshakeSession created = beginDiscovery(playerId, connectionIdentity, currentTick, deadlineTick);
        return new DiscoveryResult(created, true);
    }

    public synchronized ClientHandshakeSession beginDiscovery(UUID playerId, int connectionIdentity,
            long currentTick, long deadlineTick) {
        ClientHandshakeSession session = new ClientHandshakeSession(playerId, connectionIdentity,
                UUID.randomUUID(), ClientSyncProtocol.PROTOCOL_VERSION, ClientCapabilities.empty(),
                ClientHandshakeState.DISCOVERING, currentTick, deadlineTick, null, null);
        sessions.put(playerId, session);
        return session;
    }

    public synchronized ClientHandshakeSession begin(UUID playerId, int connectionIdentity,
            long currentTick, long deadlineTick) {
        ClientHandshakeSession session = new ClientHandshakeSession(playerId, connectionIdentity,
                UUID.randomUUID(), ClientSyncProtocol.PROTOCOL_VERSION,
                ClientCapabilities.empty(), ClientHandshakeState.PENDING, currentTick,
                deadlineTick, null, null);
        sessions.put(playerId, session);
        return session;
    }

    public synchronized ClientHandshakeResult acceptPresence(UUID playerId, int connectionIdentity,
            UUID nonce, int protocolVersion, ClientCapabilities capabilities, String modVersion,
            long currentTick, long challengeDeadlineTick) {
        ClientHandshakeSession current = sessions.get(playerId);
        if (current == null || current.state() != ClientHandshakeState.DISCOVERING
                || current.connectionIdentity() != connectionIdentity || nonce == null
                || presenceNonces.containsKey(playerId)) {
            return new ClientHandshakeResult(false, current, HANDSHAKE_INVALID);
        }
        if (currentTick >= current.deadlineTick()) {
            ClientHandshakeSession absent = withState(current, ClientHandshakeState.ABSENT,
                    currentTick, null);
            sessions.put(playerId, absent);
            return new ClientHandshakeResult(false, absent, READY_TIMEOUT);
        }
        if (protocolVersion != ClientSyncProtocol.PROTOCOL_VERSION) {
            return reject(current, PROTOCOL_MISMATCH, currentTick);
        }
        if (capabilities == null || !capabilities.contains(ClientCapability.HANDSHAKE_V1)) {
            return reject(current, CAPABILITY_MISSING, currentTick);
        }
        presenceNonces.put(playerId, nonce);
        ClientHandshakeSession pending = new ClientHandshakeSession(playerId, connectionIdentity,
                UUID.randomUUID(), protocolVersion, capabilities, ClientHandshakeState.PENDING,
                current.createdTick(), challengeDeadlineTick, null, null);
        sessions.put(playerId, pending);
        return new ClientHandshakeResult(true, pending, null);
    }

    public synchronized ClientHandshakeSession markAbsent(UUID playerId, int connectionIdentity,
            long currentTick) {
        ClientHandshakeSession session = new ClientHandshakeSession(playerId, connectionIdentity,
                UUID.randomUUID(), ClientSyncProtocol.PROTOCOL_VERSION,
                ClientCapabilities.empty(), ClientHandshakeState.ABSENT, currentTick,
                currentTick, currentTick, null);
        sessions.put(playerId, session);
        return session;
    }

    public synchronized ClientHandshakeResult accept(UUID playerId, int connectionIdentity,
            UUID challenge, int protocolVersion, ClientCapabilities capabilities,
            long currentTick) {
        ClientHandshakeSession current = sessions.get(playerId);
        if (current != null && current.state() == ClientHandshakeState.TIMED_OUT) {
            return new ClientHandshakeResult(false, current, READY_TIMEOUT);
        }
        if (current == null) {
            return reject(current, HANDSHAKE_INVALID, currentTick);
        }
        if (current.state() == ClientHandshakeState.COMPATIBLE) {
            if (current.connectionIdentity() == connectionIdentity
                    && current.challenge().equals(challenge)
                    && current.protocolVersion() == protocolVersion
                    && current.capabilities().equals(capabilities)) {
                return new ClientHandshakeResult(true, current, null);
            }
            return new ClientHandshakeResult(false, current, HANDSHAKE_INVALID);
        }
        if (current.state() != ClientHandshakeState.PENDING
                || current.connectionIdentity() != connectionIdentity
                || !current.challenge().equals(challenge)) {
            return new ClientHandshakeResult(false, current, HANDSHAKE_INVALID);
        }
        if (currentTick >= current.deadlineTick()) {
            ClientHandshakeSession timedOut = withState(current, ClientHandshakeState.TIMED_OUT,
                    currentTick, READY_TIMEOUT);
            sessions.put(playerId, timedOut);
            return new ClientHandshakeResult(false, timedOut, READY_TIMEOUT);
        }
        if (protocolVersion != ClientSyncProtocol.PROTOCOL_VERSION) {
            return reject(current, PROTOCOL_MISMATCH, currentTick);
        }
        if (capabilities == null || !capabilities.contains(ClientCapability.HANDSHAKE_V1)) {
            return reject(current, CAPABILITY_MISSING, currentTick);
        }
        ClientHandshakeSession compatible = new ClientHandshakeSession(current.playerId(),
                current.connectionIdentity(), current.challenge(), protocolVersion, capabilities,
                ClientHandshakeState.COMPATIBLE, current.createdTick(), current.deadlineTick(),
                currentTick, null);
        sessions.put(playerId, compatible);
        return new ClientHandshakeResult(true, compatible, null);
    }

    private ClientHandshakeResult reject(ClientHandshakeSession current, String error,
            long currentTick) {
        if (current == null) {
            return new ClientHandshakeResult(false, null, error);
        }
        ClientHandshakeSession incompatible = withState(current, ClientHandshakeState.INCOMPATIBLE,
                currentTick, error);
        sessions.put(current.playerId(), incompatible);
        return new ClientHandshakeResult(false, incompatible, error);
    }

    public synchronized java.util.List<ClientHandshakeSession> tick(long currentTick) {
        java.util.ArrayList<ClientHandshakeSession> timedOut = new java.util.ArrayList<>();
        sessions.replaceAll((playerId, session) -> {
            if (session.state() == ClientHandshakeState.DISCOVERING
                    && currentTick >= session.deadlineTick()) {
                ClientHandshakeSession result = withState(session, ClientHandshakeState.ABSENT,
                        currentTick, null);
                timedOut.add(result);
                return result;
            }
            if (session.state() == ClientHandshakeState.PENDING
                    && currentTick >= session.deadlineTick()) {
                ClientHandshakeSession result = withState(session, ClientHandshakeState.TIMED_OUT,
                        currentTick, READY_TIMEOUT);
                timedOut.add(result);
                return result;
            }
            return session;
        });
        return java.util.List.copyOf(timedOut);
    }

    public synchronized void disconnect(UUID playerId, int connectionIdentity) {
        ClientHandshakeSession current = sessions.get(playerId);
        if (current != null && current.connectionIdentity() == connectionIdentity) {
        sessions.remove(playerId);
            presenceNonces.remove(playerId);
        }
    }

    public synchronized Optional<ClientHandshakeSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public synchronized Map<UUID, ClientHandshakeSession> snapshot() {
        return Map.copyOf(sessions);
    }

    public synchronized void clear() {
        sessions.clear();
        presenceNonces.clear();
    }

    private static ClientHandshakeSession withState(ClientHandshakeSession session,
            ClientHandshakeState state, long completedTick, String error) {
        return new ClientHandshakeSession(session.playerId(), session.connectionIdentity(),
                session.challenge(), session.protocolVersion(), session.capabilities(), state,
                session.createdTick(), session.deadlineTick(), completedTick, error);
    }
}
