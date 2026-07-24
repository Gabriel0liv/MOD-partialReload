package com.gabriel0liv.partialreload.api;

import java.time.Clock;
import java.util.Objects;

public record PlanningContext(Clock clock) {
    public PlanningContext {
        Objects.requireNonNull(clock, "clock");
    }
}
