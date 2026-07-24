package com.gabriel0liv.partialreload.validation;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public record ValidationIssue(
        ValidationSeverity severity,
        String code,
        @Nullable ReloadCategory category,
        @Nullable ResourceLocation provider,
        @Nullable ResourceLocation resource,
        @Nullable String packId,
        String message,
        @Nullable SourceLocation sourceLocation,
        @Nullable String cause
) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public ValidationIssue(
            ValidationSeverity severity,
            String code,
            String message,
            @Nullable ResourceLocation resource
    ) {
        this(severity, code, null, null, resource, null, message, null, null);
    }
}
