package com.gabriel0liv.partialreload.advancement;

import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.*;

public record PlayerAdvancementStateSnapshot(UUID playerId,String playerName,int playerAdvancementsIdentity,
        Path savePath,boolean fileExisted,byte[] fileBytes,
        Map<ResourceLocation,Map<String,Long>> completedCriteria,Set<ResourceLocation> completedAdvancements,
        Set<ResourceLocation> visible,Set<ResourceLocation> progressChanged,Set<ResourceLocation> rootsToUpdate,
        ResourceLocation selectedTab,boolean firstPacket,boolean connectionOpen) {
    public PlayerAdvancementStateSnapshot {
        fileBytes=fileBytes.clone(); Map<ResourceLocation,Map<String,Long>> copy=new LinkedHashMap<>();
        completedCriteria.forEach((id,criteria)->copy.put(id,Map.copyOf(criteria)));completedCriteria=Collections.unmodifiableMap(copy);
        completedAdvancements=Set.copyOf(completedAdvancements);visible=Set.copyOf(visible);
        progressChanged=Set.copyOf(progressChanged);rootsToUpdate=Set.copyOf(rootsToUpdate);
    }
    @Override public byte[] fileBytes(){return fileBytes.clone();}
}
