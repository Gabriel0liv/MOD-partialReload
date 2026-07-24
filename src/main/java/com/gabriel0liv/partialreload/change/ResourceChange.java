package com.gabriel0liv.partialreload.change;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

public record ResourceChange(
        ResourceLocation location,
        ChangeKind kind,
        @Nullable ResourceDescriptor before,
        @Nullable ResourceDescriptor after
) {
    public ResourceChange {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(kind, "kind");
        if (before == null && after == null) {
            throw new IllegalArgumentException("A resource change requires a before or after descriptor");
        }
    }

    public ReloadCategory category() {
        return after != null ? after.category() : Objects.requireNonNull(before).category();
    }

    public ResourceDescriptor descriptor() {
        return after != null ? after : Objects.requireNonNull(before);
    }
}
