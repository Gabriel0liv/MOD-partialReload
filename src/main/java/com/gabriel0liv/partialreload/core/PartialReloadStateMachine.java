package com.gabriel0liv.partialreload.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class PartialReloadStateMachine {
    private static final Map<PartialReloadState, Set<PartialReloadState>> ALLOWED = allowedTransitions();
    private PartialReloadState state = PartialReloadState.IDLE;

    public synchronized PartialReloadState state() {
        return state;
    }

    public synchronized void transitionTo(PartialReloadState next) {
        if (!ALLOWED.getOrDefault(state, Set.of()).contains(next)) {
            throw new InvalidStateTransitionException(state, next);
        }
        state = next;
    }

    private static Map<PartialReloadState, Set<PartialReloadState>> allowedTransitions() {
        Map<PartialReloadState, Set<PartialReloadState>> map = new EnumMap<>(PartialReloadState.class);
        map.put(PartialReloadState.IDLE, EnumSet.of(
                PartialReloadState.SCANNING,
                PartialReloadState.PLANNING,
                PartialReloadState.PREPARING
        ));
        map.put(PartialReloadState.SCANNING, EnumSet.of(
                PartialReloadState.IDLE,
                PartialReloadState.READY,
                PartialReloadState.FAILED_SAFE
        ));
        map.put(PartialReloadState.PLANNING, EnumSet.of(PartialReloadState.READY, PartialReloadState.FAILED_SAFE));
        map.put(PartialReloadState.PREPARING, EnumSet.of(
                PartialReloadState.VALIDATING,
                PartialReloadState.FAILED_SAFE
        ));
        map.put(PartialReloadState.VALIDATING, EnumSet.of(
                PartialReloadState.READY,
                PartialReloadState.FAILED_SAFE
        ));
        map.put(PartialReloadState.READY, EnumSet.of(PartialReloadState.IDLE));
        map.put(PartialReloadState.FAILED_SAFE, EnumSet.of(PartialReloadState.IDLE));
        return Map.copyOf(map);
    }
}
