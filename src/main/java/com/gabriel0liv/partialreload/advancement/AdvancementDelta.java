package com.gabriel0liv.partialreload.advancement;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record AdvancementDelta(Set<ResourceLocation> added, Set<ResourceLocation> removed,
        Set<ResourceLocation> modified, Set<ResourceLocation> restoredFromLowerPack,
        Set<ResourceLocation> parentChanged, Set<ResourceLocation> criteriaChanged,
        Set<ResourceLocation> requirementsChanged, Set<ResourceLocation> rewardsChanged,
        Set<ResourceLocation> displayChanged) {
    public AdvancementDelta {
        added=Set.copyOf(added); removed=Set.copyOf(removed); modified=Set.copyOf(modified);
        restoredFromLowerPack=Set.copyOf(restoredFromLowerPack); parentChanged=Set.copyOf(parentChanged);
        criteriaChanged=Set.copyOf(criteriaChanged); requirementsChanged=Set.copyOf(requirementsChanged);
        rewardsChanged=Set.copyOf(rewardsChanged); displayChanged=Set.copyOf(displayChanged);
    }
}
