package com.gabriel0liv.partialreload.function;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record FunctionDependency(
        ResourceLocation source,
        ResourceLocation target,
        FunctionDependencyType type,
        int line,
        boolean tagTarget
) {
    public FunctionDependency {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        if (line < 0) throw new IllegalArgumentException("line must not be negative");
    }

    public FunctionDependency(
            ResourceLocation source,
            ResourceLocation target,
            FunctionDependencyType type,
            int line
    ) {
        this(source, target, type, line, false);
    }
}
