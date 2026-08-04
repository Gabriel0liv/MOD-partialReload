package com.gabriel0liv.partialreload.glm;

public enum GlobalLootModifierTransactionStatus {
    PREPARING, READY, COMMITTING, VERIFYING, SUCCESS,
    ROLLING_BACK, ROLLED_BACK, FAILED, DEGRADED
}
