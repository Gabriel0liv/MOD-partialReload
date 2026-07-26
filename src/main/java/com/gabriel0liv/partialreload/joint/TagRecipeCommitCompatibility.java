package com.gabriel0liv.partialreload.joint;

import net.minecraft.server.MinecraftServer;
import java.util.Set;

public record TagRecipeCommitCompatibility(boolean compatible, String detail, Set<String> supportedRegistries) {
    public static TagRecipeCommitCompatibility inspect(MinecraftServer server) {
        if (server == null) return new TagRecipeCommitCompatibility(false,"server unavailable",Set.of());
        return new TagRecipeCommitCompatibility(true,"Forge 47.4.10 public bindTags/replaceRecipes/Ingredient.invalidateAll",Set.of("items","blocks","fluids","entity_types","game_events","mob_effects","enchantments"));
    }
}
