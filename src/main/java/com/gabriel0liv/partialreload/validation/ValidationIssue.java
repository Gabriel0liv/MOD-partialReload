package com.gabriel0liv.partialreload.validation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public record ValidationIssue(
        ValidationSeverity severity,
        String code,
        String message,
        @Nullable ResourceLocation resource
) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
