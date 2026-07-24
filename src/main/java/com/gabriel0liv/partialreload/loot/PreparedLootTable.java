package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Objects;

public final class PreparedLootTable {
    private final ResourceLocation id;
    private final ResourceDescriptor source;
    private final LootTable candidate;

    PreparedLootTable(ResourceLocation id, ResourceDescriptor source, LootTable candidate) {
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

    LootTable candidate() {
        return candidate;
    }
}

