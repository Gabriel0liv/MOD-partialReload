package com.gabriel0liv.partialreload.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartialReloadStateMachineTest {
    @Test
    void acceptsPhaseOneTransitions() {
        PartialReloadStateMachine machine = new PartialReloadStateMachine();

        machine.transitionTo(PartialReloadState.SCANNING);
        machine.transitionTo(PartialReloadState.IDLE);
        machine.transitionTo(PartialReloadState.PLANNING);
        machine.transitionTo(PartialReloadState.READY);
        machine.transitionTo(PartialReloadState.IDLE);

        assertEquals(PartialReloadState.IDLE, machine.state());
    }

    @Test
    void acceptsSafeFailureRecovery() {
        PartialReloadStateMachine machine = new PartialReloadStateMachine();
        machine.transitionTo(PartialReloadState.SCANNING);
        machine.transitionTo(PartialReloadState.FAILED_SAFE);
        machine.transitionTo(PartialReloadState.IDLE);

        assertEquals(PartialReloadState.IDLE, machine.state());
    }

    @Test
    void rejectsUnimplementedAndInvalidTransitions() {
        PartialReloadStateMachine machine = new PartialReloadStateMachine();

        assertThrows(
                InvalidStateTransitionException.class,
                () -> machine.transitionTo(PartialReloadState.COMMITTING)
        );
        assertEquals(PartialReloadState.IDLE, machine.state());
    }
}
