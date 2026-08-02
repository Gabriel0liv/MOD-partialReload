package com.gabriel0liv.partialreload.joint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeferredClientRefreshTrackerTest {
    @Test
    void generationAndStaleLifecycleAreExplicit() {
        DeferredClientRefreshTracker tracker = new DeferredClientRefreshTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(0, tracker.activeGeneration());
        assertEquals(1, tracker.confirmCommit(
                TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN,
                List.of(first, second, first)));
        assertTrue(tracker.lastCommitDeferred());
        assertEquals(2, tracker.staleCount());
        assertTrue(tracker.isStale(first));
        assertTrue(tracker.isStale(second));

        tracker.remove(first);
        assertFalse(tracker.isStale(first));
        assertEquals(1, tracker.staleCount());

        assertEquals(2, tracker.confirmCommit(
                TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN,
                List.of(first)));
        assertTrue(tracker.isStale(first));
        assertFalse(tracker.isStale(second));
        assertEquals(1, tracker.staleCount());

        tracker.clear();
        assertEquals(0, tracker.activeGeneration());
        assertEquals(0, tracker.staleCount());
        assertFalse(tracker.lastCommitDeferred());
    }

    @Test
    void rejectCommitStillAdvancesServerGenerationWithoutStalePlayers() {
        DeferredClientRefreshTracker tracker = new DeferredClientRefreshTracker();
        assertEquals(1, tracker.confirmCommit(TagRecipeConnectedPlayerPolicy.REJECT, List.of()));
        assertFalse(tracker.lastCommitDeferred());
        assertEquals(0, tracker.staleCount());
    }

    @Test
    void snapshotCanBeRestoredWithoutAliasing() {
        DeferredClientRefreshTracker tracker = new DeferredClientRefreshTracker();
        UUID player = UUID.randomUUID();
        tracker.confirmCommit(TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN, List.of(player));
        DeferredClientRefreshTracker.Snapshot snapshot = tracker.snapshot();
        tracker.clear();
        tracker.restore(snapshot);
        assertEquals(1, tracker.activeGeneration());
        assertTrue(tracker.isStale(player));
    }

    @Test
    void connectedPlayerPolicyIsFailClosed() {
        assertDoesNotThrow(() -> TagRecipeConnectedPlayerPolicy.REJECT.validateConnectedPlayerCount(0));
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> TagRecipeConnectedPlayerPolicy.REJECT.validateConnectedPlayerCount(1));
        assertEquals("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED", rejected.getMessage());
        assertDoesNotThrow(() -> TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN
                .validateConnectedPlayerCount(2));
        assertThrows(IllegalArgumentException.class,
                () -> TagRecipeConnectedPlayerPolicy.DEFER_CLIENT_REFRESH_UNTIL_RELOGIN
                        .validateConnectedPlayerCount(-1));
    }
}
