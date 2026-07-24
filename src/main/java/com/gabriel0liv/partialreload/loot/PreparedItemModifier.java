package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import java.util.Objects;

public final class PreparedItemModifier {
    private final ResourceLocation id;
    private final ResourceDescriptor source;
    private final LootItemFunction candidate;

    PreparedItemModifier(ResourceLocation id, ResourceDescriptor source, LootItemFunction candidate) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.candidate = Objects.requireNonNull(candidate, "candidate");
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceDescriptor source() {
        return source;
    }

    LootItemFunction candidate() {
        return candidate;
    }
}

