package com.gabriel0liv.partialreload.api;

import java.util.Objects;
import java.util.Set;

public record ReloadEnvironment(boolean dedicatedServer, Set<String> loadedMods) {
    public ReloadEnvironment {
        loadedMods = Set.copyOf(Objects.requireNonNull(loadedMods, "loadedMods"));
    }
}
