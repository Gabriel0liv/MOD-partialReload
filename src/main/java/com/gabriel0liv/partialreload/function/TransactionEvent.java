package com.gabriel0liv.partialreload.function;

import java.time.Instant;
import java.util.Objects;

public record TransactionEvent(Instant at, TransactionEventType type, String detail) {
    public TransactionEvent {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
    }
}
