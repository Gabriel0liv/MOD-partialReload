package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Objects;

public final class PreparedPredicate {
    private final ResourceLocation id;
    private final ResourceDescriptor source;
    private final LootItemCondition candidate;

    PreparedPredicate(ResourceLocation id, ResourceDescriptor source, LootItemCondition candidate) {
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

    LootItemCondition candidate() {
        return candidate;
    }
}

