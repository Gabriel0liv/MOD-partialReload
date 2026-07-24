package com.gabriel0liv.partialreload.loot;

import java.util.Objects;

public record LootDataDelta(
        LootTypeDelta predicates,
        LootTypeDelta itemModifiers,
        LootTypeDelta lootTables
) {
    public LootDataDelta {
        Objects.requireNonNull(predicates, "predicates");
        Objects.requireNonNull(itemModifiers, "itemModifiers");
        Objects.requireNonNull(lootTables, "lootTables");
    }

    public long changedCount() {
        return predicates.changedCount() + itemModifiers.changedCount() + lootTables.changedCount();
    }
}

