package com.gabriel0liv.partialreload.advancement;

import net.minecraft.advancements.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Exact 1.20.1 bridge. Field visibility is supplied only by the minimal AT. */
public final class ServerAdvancementManagerBridge {
    private ServerAdvancementManagerBridge(){}
    public static void validateLayout(ServerAdvancementManager manager){
        if(manager==null||manager.advancements==null||manager.advancements.getClass()!=AdvancementList.class)
            throw new IllegalStateException("ADVANCEMENT_MANAGER_LAYOUT_UNSUPPORTED");
    }
    public static ActiveAdvancementGeneration capture(ServerAdvancementManager manager){
        validateLayout(manager); Map<ResourceLocation,Advancement> values=new LinkedHashMap<>();
        manager.getAllAdvancements().forEach(a->values.put(a.getId(),a));
        return generation(manager.advancements,values);
    }
    public static ActiveAdvancementGeneration fromPrepared(PreparedAdvancements prepared){
        return generation(prepared.candidateList(),prepared.advancements());
    }
    public static void publish(ServerAdvancementManager manager,ActiveAdvancementGeneration generation){
        validateLayout(manager);validateGeneration(generation);manager.advancements=generation.list();
    }
    public static boolean matchesExactly(ServerAdvancementManager manager,ActiveAdvancementGeneration expected){
        validateLayout(manager); if(manager.advancements!=expected.list())return false;
        Map<ResourceLocation,Advancement> actual=new LinkedHashMap<>();manager.getAllAdvancements().forEach(a->actual.put(a.getId(),a));
        if(!actual.keySet().equals(expected.advancements().keySet()))return false;
        for(var e:expected.advancements().entrySet())if(actual.get(e.getKey())!=e.getValue())return false;
        return AdvancementListSnapshot.from(actual.values()).equals(expected.tree());
    }
    public static void verify(ServerAdvancementManager manager,ActiveAdvancementGeneration expected){
        if(!matchesExactly(manager,expected))throw new IllegalStateException("ADVANCEMENT_COMMIT_VERIFICATION_FAILED");
        expected.advancements().forEach((id,value)->{if(manager.getAdvancement(id)!=value)throw new IllegalStateException("ADVANCEMENT_COMMIT_VERIFICATION_FAILED: "+id);});
    }
    private static ActiveAdvancementGeneration generation(AdvancementList list,Map<ResourceLocation,Advancement> values){
        AdvancementListSnapshot tree=AdvancementListSnapshot.from(values.values());
        return new ActiveAdvancementGeneration(list,values,tree,UUID.randomUUID(),digest(values,tree));
    }
    private static void validateGeneration(ActiveAdvancementGeneration generation){
        if(generation.list()==null||generation.advancements()==null||generation.tree()==null)throw new IllegalStateException("ADVANCEMENT_COMMIT_CANDIDATE_INCOMPLETE");
        if(!generation.advancements().keySet().equals(generation.tree().children().keySet()))throw new IllegalStateException("ADVANCEMENT_COMMIT_CANDIDATE_INCOMPLETE");
    }
    private static String digest(Map<ResourceLocation,Advancement> values,AdvancementListSnapshot tree){
        try{List<String> rows=new ArrayList<>();values.forEach((id,value)->rows.add(id+":"+System.identityHashCode(value)));rows.add(tree.toString());Collections.sort(rows);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",rows).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
