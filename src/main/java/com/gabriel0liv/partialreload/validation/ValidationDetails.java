package com.gabriel0liv.partialreload.validation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record ValidationDetails(
        @Nullable String dataType,
        @Nullable ResourceLocation logicalPath,
        @Nullable String jsonPath,
        @Nullable String dependencyPath,
        @Nullable String serializer
) {
}

