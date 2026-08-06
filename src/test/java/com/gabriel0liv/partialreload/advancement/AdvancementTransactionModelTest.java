package com.gabriel0liv.partialreload.advancement;

import com.google.common.collect.ImmutableMultimap;
import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;
import net.minecraft.advancements.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementTransactionModelTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("partialreload_test",path);}
    private static ActiveAdvancementGeneration generation(String suffix){AdvancementList list=new AdvancementList();Map<ResourceLocation,Advancement.Builder> builders=new LinkedHashMap<>();builders.put(id("root_"+suffix),Advancement.Builder.advancement());builders.put(id("child_"+suffix),Advancement.Builder.advancement().parent(id("root_"+suffix)));list.add(builders);Map<ResourceLocation,Advancement> values=new LinkedHashMap<>();list.getAllAdvancements().forEach(a->values.put(a.getId(),a));return new ActiveAdvancementGeneration(list,values,AdvancementListSnapshot.from(values.values()),UUID.randomUUID(),suffix);}
    @AfterEach void clear(){AdvancementFaultInjection.clear();}
    @Test void listSnapshotCapturesRootParentAndChildren(){var g=generation("a");assertTrue(g.tree().roots().contains(id("root_a")));assertEquals(id("root_a"),g.tree().parents().get(id("child_a")));assertTrue(g.tree().children().get(id("root_a")).contains(id("child_a")));}
    @Test void activeGenerationMapIsImmutable(){var g=generation("a");assertThrows(UnsupportedOperationException.class,()->g.advancements().clear());}
    @Test void candidateListAndMapShareExactAdvancementReferences(){var g=generation("b");g.advancements().forEach((id,value)->assertSame(value,g.list().get(id)));}
    @Test void exactGuardModelRejectsDifferentObjectAtSameId(){var a=generation("same");var b=generation("same");assertEquals(a.advancements().keySet(),b.advancements().keySet());a.advancements().forEach((id,value)->assertNotSame(value,b.advancements().get(id)));}
    @Test void retainedGenerationKeepsItsOwnTreeAndReferences(){var a=generation("a");var b=generation("b");assertTrue(a.tree().roots().contains(id("root_a")));assertFalse(a.tree().roots().contains(id("root_b")));assertNotEquals(a.advancements().keySet(),b.advancements().keySet());}
    @Test void deltaCollectionsAreImmutable(){var d=new AdvancementDelta(Set.of(id("a")),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of());assertThrows(UnsupportedOperationException.class,()->d.added().clear());}
    @Test void allTransactionStatesExist(){assertArrayEquals(new AdvancementTransactionStatus[]{AdvancementTransactionStatus.PREPARING,AdvancementTransactionStatus.READY,AdvancementTransactionStatus.QUIESCING,AdvancementTransactionStatus.COMMITTING,AdvancementTransactionStatus.REBINDING_PLAYERS,AdvancementTransactionStatus.SYNCING_CLIENTS,AdvancementTransactionStatus.VERIFYING,AdvancementTransactionStatus.SUCCESS,AdvancementTransactionStatus.ROLLING_BACK,AdvancementTransactionStatus.ROLLED_BACK,AdvancementTransactionStatus.FAILED,AdvancementTransactionStatus.DEGRADED},AdvancementTransactionStatus.values());}
    @Test void allFaultPointsExist(){assertEquals(8,AdvancementFaultPoint.values().length);}
    @Test void faultIsOneShot(){AdvancementFaultInjection.arm(AdvancementFaultPoint.AFTER_MANAGER_PUBLICATION);assertThrows(IllegalStateException.class,()->AdvancementFaultInjection.hit(AdvancementFaultPoint.AFTER_MANAGER_PUBLICATION));assertDoesNotThrow(()->AdvancementFaultInjection.hit(AdvancementFaultPoint.AFTER_MANAGER_PUBLICATION));}
    @Test void transactionTracksPlayersAndSync(){var tx=transaction();UUID player=UUID.randomUUID();tx.playerRebound(player);tx.clientSynced();assertEquals(Set.of(player),tx.playersRebound());assertEquals(1,tx.clientsSynced());}
    @Test void transactionPreservesFailureAndRollback(){var tx=transaction();tx.failure("boom");tx.rollbackPerformed(true);tx.status(AdvancementTransactionStatus.ROLLED_BACK);assertEquals("boom",tx.failure());assertTrue(tx.rollbackPerformed());assertEquals(AdvancementTransactionStatus.ROLLED_BACK,tx.status());}
    @Test void snapshotDefensivelyCopiesFileBytes(){byte[] bytes={1,2};var s=new PlayerAdvancementStateSnapshot(UUID.randomUUID(),"p",1,java.nio.file.Path.of("x"),true,bytes,Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),null,false,true);bytes[0]=9;assertEquals(1,s.fileBytes()[0]);byte[] returned=s.fileBytes();returned[0]=8;assertEquals(1,s.fileBytes()[0]);}
    private static AdvancementCommitTransaction transaction(){var loot=new ActiveLootDataGeneration(Map.of(),ImmutableMultimap.of(),UUID.randomUUID(),"x");var deps=new AdvancementDependencySnapshot(1,loot,2,Map.of(),3,4,Set.of(),5,"r");return new AdvancementCommitTransaction(UUID.randomUUID(),UUID.randomUUID(),Instant.EPOCH,"test",7,generation("a"),deps);}
}
