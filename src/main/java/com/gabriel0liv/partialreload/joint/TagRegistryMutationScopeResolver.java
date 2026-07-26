package com.gabriel0liv.partialreload.joint;

public final class TagRegistryMutationScopeResolver {
    public enum Operation { ADD, MODIFY, REMOVE }
    private TagRegistryMutationScopeResolver() {}
    public static boolean supported(String registryPath, Operation operation, String namespace) {
        return switch (registryPath) {
            case "items", "blocks", "fluids", "entity_types", "game_events", "mob_effects", "enchantments" -> true;
            default -> false;
        };
    }
    public static String blocker(String registryPath, Operation operation, String namespace) {
        return supported(registryPath, operation, namespace) ? null : "TAG_REGISTRY_COMMIT_UNSUPPORTED: " + registryPath;
    }
}
