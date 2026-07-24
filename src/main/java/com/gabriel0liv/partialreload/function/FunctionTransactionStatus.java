package com.gabriel0liv.partialreload.function;

public enum FunctionTransactionStatus {
    REQUESTED,
    QUIESCING,
    COMMITTING,
    VERIFYING,
    SUCCESS,
    ROLLED_BACK,
    FAILED_SAFE,
    DEGRADED
}
