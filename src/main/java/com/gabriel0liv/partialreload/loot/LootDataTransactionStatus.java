package com.gabriel0liv.partialreload.loot;

public enum LootDataTransactionStatus {
    PREPARING,
    READY,
    COMMITTING,
    VERIFYING,
    SUCCESS,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED,
    DEGRADED
}
