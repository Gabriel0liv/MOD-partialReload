package com.gabriel0liv.partialreload.resource;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ResourceDescriptor(
        ResourceLocation location,
        ResourceLocation logicalId,
        ReloadCategory category,
        String sourcePack,
        ResourceFingerprint fingerprint
) {
    public ResourceDescriptor {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(logicalId, "logicalId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(sourcePack, "sourcePack");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
