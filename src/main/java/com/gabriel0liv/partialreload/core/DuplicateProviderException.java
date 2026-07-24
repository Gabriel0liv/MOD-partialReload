package com.gabriel0liv.partialreload.core;

import net.minecraft.resources.ResourceLocation;

public final class DuplicateProviderException extends IllegalArgumentException {
    public DuplicateProviderException(ResourceLocation id) {
        super("A reload provider with ID " + id + " is already registered");
    }
}
