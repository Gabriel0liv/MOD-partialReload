package com.gabriel0liv.partialreload.joint;

import com.gabriel0liv.partialreload.recipe.ActiveRecipeGeneration;
import com.gabriel0liv.partialreload.tags.ActiveTagGeneration;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import java.time.Instant; import java.util.UUID;

public record ActiveTagRecipeGeneration(UUID generationId, Instant capturedAt,
                                        ActiveTagGeneration tags, ActiveRecipeGeneration recipes,
                                        ResourceSnapshot sourceSnapshot) {}
