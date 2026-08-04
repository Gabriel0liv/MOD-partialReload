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
    GLOBAL_LOOT_MODIFIERS,
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
        if ("glm".equalsIgnoreCase(value) || "global_loot_modifiers".equalsIgnoreCase(value)) {
            return Optional.of(GLOBAL_LOOT_MODIFIERS);
        }
        return Arrays.stream(values())
                .filter(category -> category.commandName().equals(value.toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
