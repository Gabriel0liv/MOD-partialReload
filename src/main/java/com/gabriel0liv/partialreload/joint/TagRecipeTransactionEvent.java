package com.gabriel0liv.partialreload.joint;

import java.time.Instant;
import java.util.UUID;

public record TagRecipeTransactionEvent(Instant at, UUID transactionId, TagRecipeTransactionStatus status,
                                        TagRecipeTransactionEventType type, String detail) {
    public TagRecipeTransactionEvent(Instant at, TagRecipeTransactionStatus status, String detail) {
        this(at, null, status, detail != null && detail.startsWith("FAILURE:")
                ? TagRecipeTransactionEventType.FAILURE : TagRecipeTransactionEventType.STATUS_CHANGED, detail);
    }
}
