package com.gabriel0liv.partialreload.core;

public final class InvalidStateTransitionException extends IllegalStateException {
    public InvalidStateTransitionException(PartialReloadState from, PartialReloadState to) {
        super("Invalid partial reload state transition: " + from + " -> " + to);
    }
}
