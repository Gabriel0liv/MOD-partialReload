package com.gabriel0liv.partialreload.joint;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DeferredClientRefreshTracker {
    private long activeGeneration;
    private boolean lastCommitDeferred;
    private final Map<UUID, Long> stalePlayers = new LinkedHashMap<>();

    public synchronized long confirmCommit(TagRecipeConnectedPlayerPolicy policy, Collection<UUID> connectedPlayers) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(connectedPlayers, "connectedPlayers");
        activeGeneration++;
        lastCommitDeferred = policy == TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN;
        stalePlayers.clear();
        if (lastCommitDeferred) {
            for (UUID playerId : connectedPlayers) {
                stalePlayers.put(Objects.requireNonNull(playerId, "playerId"), activeGeneration);
            }
        }
        return activeGeneration;
    }

    public synchronized void remove(UUID playerId) {
        stalePlayers.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void clear() {
        activeGeneration = 0L;
        lastCommitDeferred = false;
        stalePlayers.clear();
    }

    public synchronized long activeGeneration() {
        return activeGeneration;
    }

    public synchronized boolean lastCommitDeferred() {
        return lastCommitDeferred;
    }

    public synchronized boolean isStale(UUID playerId) {
        return stalePlayers.containsKey(playerId);
    }

    public synchronized int staleCount() {
        return stalePlayers.size();
    }

    public synchronized Set<UUID> stalePlayerIds() {
        return Set.copyOf(stalePlayers.keySet());
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(activeGeneration, lastCommitDeferred, Map.copyOf(stalePlayers));
    }

    public synchronized void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        activeGeneration = snapshot.activeGeneration();
        lastCommitDeferred = snapshot.lastCommitDeferred();
        stalePlayers.clear();
        stalePlayers.putAll(snapshot.stalePlayers());
    }

    public record Snapshot(long activeGeneration, boolean lastCommitDeferred, Map<UUID, Long> stalePlayers) {
        public Snapshot {
            if (activeGeneration < 0) throw new IllegalArgumentException("activeGeneration");
            stalePlayers = Map.copyOf(stalePlayers);
        }
    }
}
