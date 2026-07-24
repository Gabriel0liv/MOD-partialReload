package com.gabriel0liv.partialreload.core;

public enum PartialReloadState {
    IDLE,
    SCANNING,
    PLANNING,
    PREPARING,
    VALIDATING,
    READY,
    QUIESCING,
    COMMITTING,
    SYNCHRONIZING,
    VERIFYING,
    SUCCESS,
    ROLLED_BACK,
    FAILED_SAFE,
    DEGRADED
}
