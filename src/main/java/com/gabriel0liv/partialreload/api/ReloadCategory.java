package com.gabriel0liv.partialreload.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ReloadCategory {
    FUNCTIONS,
    ADVANCEMENTS,
    PREDICATES,
    RECIPES,
    LOOT,
    ITEM_MODIFIERS,
    TAGS,
    ORIGINS,
    KUBEJS,
    SILENTGEAR,
    DYNAMIC_REGISTRIES,
    UNKNOWN;

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ReloadCategory> fromCommandName(String value) {
        return Arrays.stream(values())
                .filter(category -> category.commandName().equals(value.toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
