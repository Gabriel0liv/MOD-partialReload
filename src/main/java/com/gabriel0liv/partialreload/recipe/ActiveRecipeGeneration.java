package com.gabriel0liv.partialreload.recipe;

import net.minecraft.world.item.crafting.Recipe;
import java.time.Instant;
import java.util.*;

public final class ActiveRecipeGeneration {
    private final UUID generationId; private final Instant capturedAt; private final List<Recipe<?>> recipes;
    public ActiveRecipeGeneration(UUID id, Instant at, Collection<Recipe<?>> recipes){this.generationId=id;this.capturedAt=at;this.recipes=List.copyOf(recipes);}
    public UUID generationId(){return generationId;} public Instant capturedAt(){return capturedAt;} public List<Recipe<?>> recipes(){return recipes;}
}
