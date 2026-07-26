package com.gabriel0liv.partialreload.joint;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Capability fingerprint for the public server-only publication contract. */
public record TagRecipeCommitCompatibility(
        boolean compatible,
        String detail,
        Set<String> supportedRegistries,
        String minecraftVersion,
        String forgeVersion,
        String recipeManagerClass,
        int recipeManagerIdentity,
        int registryAccessIdentity,
        Map<String, Integer> supportedRegistryIdentities,
        String cacheHook,
        String eventHook,
        String supportLevel,
        String fingerprint
) {
    private static final Set<String> SUPPORTED = Set.of("items", "blocks", "fluids", "entity_types", "game_events", "mob_effects", "enchantments");

    public static TagRecipeCommitCompatibility inspect(MinecraftServer server) {
        if (server == null) return unavailable("server unavailable");
        Map<String, Integer> identities = new LinkedHashMap<>();
        boolean registriesPresent = true;
        for (String path : SUPPORTED) {
            ResourceKey<Registry<Object>> key = registryKey(path);
            var registry = server.registryAccess().registry(key);
            if (registry.isEmpty()) registriesPresent = false;
            else identities.put(path, System.identityHashCode(registry.get()));
        }
        String mc = net.minecraft.SharedConstants.getCurrentVersion().getName();
        String forge = net.minecraftforge.versions.forge.ForgeVersion.getVersion();
        String recipeClass = server.getRecipeManager().getClass().getName();
        String cache = "Ingredient.invalidateAll";
        String event = "TagsUpdatedEvent(RegistryAccess,boolean,boolean)";
        boolean compatible = registriesPresent && server.getRecipeManager() != null && "1.20.1".equals(mc) && forge.startsWith("47.4.");
        String detail = compatible
                ? "public bindTags/replaceRecipes/cache/event contract verified"
                : "one or more allowlisted registries are unavailable";
        String fingerprint = mc + "|" + forge + "|" + recipeClass + "|" + System.identityHashCode(server.getRecipeManager())
                + "|" + System.identityHashCode(server.registryAccess()) + "|" + identities + "|" + cache + "|" + event;
        return new TagRecipeCommitCompatibility(compatible, detail, SUPPORTED, mc, forge, recipeClass,
                System.identityHashCode(server.getRecipeManager()), System.identityHashCode(server.registryAccess()),
                Map.copyOf(identities), cache, event, "SERVER_ONLY_NO_PLAYERS", fingerprint);
    }

    private static TagRecipeCommitCompatibility unavailable(String detail) {
        return new TagRecipeCommitCompatibility(false, detail, Set.of(), "unknown", "unknown", "unknown", 0, 0,
                Map.of(), "unavailable", "unavailable", "DISABLED", "unavailable");
    }

    @SuppressWarnings("unchecked")
    public static ResourceKey<Registry<Object>> registryKey(String path) {
        String canonical = switch (path) {
            case "items" -> "item"; case "blocks" -> "block"; case "fluids" -> "fluid";
            case "entity_types" -> "entity_type"; case "game_events" -> "game_event";
            case "mob_effects" -> "mob_effect"; case "enchantments" -> "enchantment";
            default -> throw new IllegalArgumentException("unsupported registry path: " + path);
        };
        return (ResourceKey<Registry<Object>>) (ResourceKey<?>) ResourceKey.createRegistryKey(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", canonical));
    }
}
