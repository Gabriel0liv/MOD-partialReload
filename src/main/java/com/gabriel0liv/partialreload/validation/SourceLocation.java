package com.gabriel0liv.partialreload.validation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public record SourceLocation(
        ResourceLocation file,
        int line,
        @Nullable Integer column,
        @Nullable Integer cursor,
        String command
) {
    public SourceLocation {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(command, "command");
        if (line < 1) throw new IllegalArgumentException("line must be positive");
        if (column != null && column < 1) throw new IllegalArgumentException("column must be positive");
        if (cursor != null && cursor < 0) throw new IllegalArgumentException("cursor must not be negative");
    }
}
