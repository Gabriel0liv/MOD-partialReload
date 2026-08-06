package com.gabriel0liv.partialreload.advancement;

import net.minecraft.advancements.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.util.*;

/** Typed access to the exact PlayerAdvancements layout authorized by the AT. */
public final class PlayerAdvancementBridge {
    private PlayerAdvancementBridge(){}
    public static void validateLayout(PlayerAdvancements value){
        if(value==null||value.playerSavePath==null||value.progress==null||value.visible==null||value.progressChanged==null||value.rootsToUpdate==null)
            throw new IllegalStateException("ADVANCEMENT_COMMIT_PLAYER_STATE_UNAVAILABLE");
    }
    public static PlayerAdvancementStateSnapshot saveAndCapture(ServerPlayer player){
        PlayerAdvancements value=player.getAdvancements();validateLayout(value);value.save();Path path=value.playerSavePath;
        try{
            boolean exists=Files.isRegularFile(path);byte[] bytes=exists?Files.readAllBytes(path):new byte[0];
            Map<ResourceLocation,Map<String,Long>> criteria=new LinkedHashMap<>();Set<ResourceLocation> completed=new LinkedHashSet<>();
            value.progress.forEach((advancement,progress)->{Map<String,Long> dates=new LinkedHashMap<>();for(String name:progress.getCompletedCriteria()){CriterionProgress cp=progress.getCriterion(name);dates.put(name,cp==null||cp.getObtained()==null?0L:serializedDate(cp.getObtained().getTime()));}criteria.put(advancement.getId(),dates);if(progress.isDone())completed.add(advancement.getId());});
            return new PlayerAdvancementStateSnapshot(player.getUUID(),player.getGameProfile().getName(),System.identityHashCode(value),path,exists,bytes,criteria,completed,
                    ids(value.visible),ids(value.progressChanged),ids(value.rootsToUpdate),value.lastSelectedTab==null?null:value.lastSelectedTab.getId(),value.isFirstPacket,player.connection.connection.isConnected());
        }catch(Exception ex){throw new IllegalStateException("ADVANCEMENT_COMMIT_PLAYER_STATE_UNAVAILABLE: "+message(ex),ex);}
    }
    public static void rebind(ServerPlayer player,ServerAdvancementManager manager,PlayerAdvancementStateSnapshot before){
        PlayerAdvancements value=player.getAdvancements();if(System.identityHashCode(value)!=before.playerAdvancementsIdentity())throw new IllegalStateException("ADVANCEMENT_COMMIT_PLAYER_STATE_UNAVAILABLE: identity changed");
        value.reload(manager);Advancement tab=before.selectedTab()==null?null:manager.getAdvancement(before.selectedTab());
        manager.advancements.getRoots().forEach(value.rootsToUpdate::add);value.isFirstPacket=true;value.flushDirty(player);value.setSelectedTab(tab);
        verifyCompatibleProgress(value,manager,before);
    }
    public static void restoreFileAndRebind(ServerPlayer player,ServerAdvancementManager manager,PlayerAdvancementStateSnapshot before){
        try{if(before.fileExisted()){Files.createDirectories(before.savePath().getParent());Path tmp=before.savePath().resolveSibling(before.savePath().getFileName()+".partialreload.tmp");Files.write(tmp,before.fileBytes());try{Files.move(tmp,before.savePath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,before.savePath(),StandardCopyOption.REPLACE_EXISTING);}}else Files.deleteIfExists(before.savePath());}
        catch(Exception ex){throw new IllegalStateException("ADVANCEMENT_ROLLBACK_PLAYER_FILE_FAILED: "+message(ex),ex);}
        rebind(player,manager,before);
    }
    public static void verifyCompatibleProgress(PlayerAdvancements value,ServerAdvancementManager manager,PlayerAdvancementStateSnapshot before){
        for(var advancementEntry:before.completedCriteria().entrySet()){
            Advancement current=manager.getAdvancement(advancementEntry.getKey());if(current==null)continue;
            AdvancementProgress progress=value.getOrStartProgress(current);
            for(var criterion:advancementEntry.getValue().entrySet())if(current.getCriteria().containsKey(criterion.getKey())){
                CriterionProgress observed=progress.getCriterion(criterion.getKey());if(observed==null||!observed.isDone()||observed.getObtained()==null||serializedDate(observed.getObtained().getTime())!=criterion.getValue())throw new IllegalStateException("ADVANCEMENT_PROGRESS_VERIFICATION_FAILED: "+advancementEntry.getKey()+"/"+criterion.getKey());
            }
        }
    }
    private static long serializedDate(long millis){return Math.floorDiv(millis,1000L)*1000L;}
    private static Set<ResourceLocation> ids(Collection<Advancement> values){Set<ResourceLocation> result=new LinkedHashSet<>();values.forEach(a->result.add(a.getId()));return result;}
    private static String message(Throwable value){return value.getMessage()==null?value.getClass().getSimpleName():value.getMessage();}
}
