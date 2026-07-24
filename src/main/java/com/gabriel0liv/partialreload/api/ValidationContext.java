package com.gabriel0liv.partialreload.api;

import java.util.Objects;

public record ValidationContext(ReloadEnvironment environment) {
    public ValidationContext {
        Objects.requireNonNull(environment, "environment");
    }
}
