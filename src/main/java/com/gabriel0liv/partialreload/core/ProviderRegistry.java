package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {
    private final Map<ResourceLocation, ReloadProvider> providers = new LinkedHashMap<>();

    public synchronized void register(ReloadProvider provider) {
        if (providers.containsKey(provider.id())) {
            throw new DuplicateProviderException(provider.id());
        }
        if (provider.categories().isEmpty()) {
            throw new IllegalArgumentException("Provider " + provider.id() + " must declare at least one category");
        }
        providers.put(provider.id(), provider);
    }

    public synchronized Optional<ReloadProvider> get(ResourceLocation id) {
        return Optional.ofNullable(providers.get(id));
    }

    public synchronized List<ReloadProvider> providersFor(ReloadCategory category) {
        return providers.values().stream().filter(provider -> provider.categories().contains(category)).toList();
    }

    public synchronized List<ReloadProvider> all() {
        return List.copyOf(providers.values());
    }
}
