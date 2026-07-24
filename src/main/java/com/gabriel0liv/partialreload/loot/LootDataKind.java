package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import net.minecraft.world.level.storage.loot.LootDataType;

public enum LootDataKind {
    PREDICATE(ReloadCategory.PREDICATES, "predicates"),
    ITEM_MODIFIER(ReloadCategory.ITEM_MODIFIERS, "item_modifiers"),
    LOOT_TABLE(ReloadCategory.LOOT, "loot_tables");

    private final ReloadCategory category;
    private final String directory;

    LootDataKind(ReloadCategory category, String directory) {
        this.category = category;
        this.directory = directory;
    }

    public ReloadCategory category() {
        return category;
    }

    public LootDataType<?> type() {
        return switch (this) {
            case PREDICATE -> LootDataType.PREDICATE;
            case ITEM_MODIFIER -> LootDataType.MODIFIER;
            case LOOT_TABLE -> LootDataType.TABLE;
        };
    }

    public String directory() {
        return directory;
    }
}
