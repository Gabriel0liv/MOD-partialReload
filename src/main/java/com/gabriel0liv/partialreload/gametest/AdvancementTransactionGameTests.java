package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.advancement.*;
import net.minecraft.advancements.*;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancementTransactionGameTests {
 private static final String BATCH="phase4i-advancement-transaction";
 private static final ResourceLocation ROOT=id("root"),CHILD=id("child"),REMOVED=id("removed"),ADDED=id("added");
 private AdvancementTransactionGameTests(){}
 @FunctionalInterface private interface Check{void run(GameTestHelper h,ServerAdvancementManager manager,ActiveAdvancementGeneration a,ActiveAdvancementGeneration b);}
 private static void scenario(GameTestHelper h,Check check){ServerAdvancementManager manager=h.getLevel().getServer().getAdvancements();ActiveAdvancementGeneration original=ServerAdvancementManagerBridge.capture(manager),a=make(false),b=make(true);try{check.run(h,manager,a,b);h.succeed();}catch(Throwable failure){h.fail(failure.getMessage()==null?failure.toString():failure.getMessage());}finally{AdvancementFaultInjection.clear();ServerAdvancementManagerBridge.publish(manager,original);}}
 @GameTest(template="empty",batch=BATCH)public static void advancementAToB(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,a);ServerAdvancementManagerBridge.publish(m,b);ServerAdvancementManagerBridge.verify(m,b);});}
 @GameTest(template="empty",batch=BATCH)public static void advancementAdded(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(m.getAdvancement(ADDED)!=null,"added advancement absent");});}
 @GameTest(template="empty",batch=BATCH)public static void advancementRemoved(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(m.getAdvancement(REMOVED)==null,"removed advancement remained");});}
 @GameTest(template="empty",batch=BATCH)public static void parentChanged(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(m.getAdvancement(CHILD).getParent()==m.getAdvancement(ADDED),"parent not changed");});}
 @GameTest(template="empty",batch=BATCH)public static void parentMissing(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementList list=new AdvancementList();list.add(Map.of(CHILD,Advancement.Builder.advancement().parent(id("missing"))));x.assertTrue(!list.getAllAdvancements().iterator().hasNext(),"missing parent built");});}
 @GameTest(template="empty",batch=BATCH)public static void parentCycle(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementList list=new AdvancementList();list.add(Map.of(ROOT,Advancement.Builder.advancement().parent(CHILD),CHILD,Advancement.Builder.advancement().parent(ROOT)));x.assertTrue(!list.getAllAdvancements().iterator().hasNext(),"cycle built");});}
 @GameTest(template="empty",batch=BATCH)public static void criterionChanged(GameTestHelper h){scenario(h,(x,m,a,b)->{x.assertTrue(b.advancements().get(CHILD).getCriteria().containsKey("new"),"new criterion absent");});}
 @GameTest(template="empty",batch=BATCH)public static void criterionPreserved(GameTestHelper h){scenario(h,(x,m,a,b)->{x.assertTrue(a.advancements().get(CHILD).getCriteria().containsKey("kept")&&b.advancements().get(CHILD).getCriteria().containsKey("kept"),"criterion not preserved");});}
 @GameTest(template="empty",batch=BATCH)public static void requirementChanged(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementProgress p=new AdvancementProgress();p.update(b.advancements().get(CHILD).getCriteria(),b.advancements().get(CHILD).getRequirements());x.assertTrue(!p.isDone(),"new requirements unexpectedly done");});}
 @GameTest(template="empty",batch=BATCH)public static void moddedTriggerValid(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(b.advancements().get(CHILD).getCriteria().get("kept").getTrigger()!=null,"trigger absent"));}
 @GameTest(template="empty",batch=BATCH)public static void unknownTriggerRejected(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(net.minecraft.advancements.CriteriaTriggers.getCriterion(id("unknown"))==null,"unknown trigger registered"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardRecipeValid(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(AdvancementRewards.EMPTY.getRecipes().length==0,"empty reward changed"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardRecipeMissing(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(id("missing")).isEmpty(),"missing recipe exists"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardFunctionValid(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(AdvancementRewards.EMPTY.serializeToJson().isJsonNull(),"empty rewards changed"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardFunctionMissing(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(h.getLevel().getServer().getFunctions().get(id("missing")).isEmpty(),"missing function exists"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardLootValid(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(h.getLevel().getServer().getLootData()!=null,"loot manager absent"));}
 @GameTest(template="empty",batch=BATCH)public static void rewardNotRepeated(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(b.advancements().values().stream().noneMatch(v->v.getCriteria().isEmpty()&&!v.getRewards().serializeToJson().isJsonNull()),"unsafe auto reward"));}
 @GameTest(template="empty",batch=BATCH)public static void completedProgressPreserved(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementProgress p=new AdvancementProgress();p.update(a.advancements().get(CHILD).getCriteria(),a.advancements().get(CHILD).getRequirements());p.grantProgress("kept");p.update(b.advancements().get(CHILD).getCriteria(),b.advancements().get(CHILD).getRequirements());x.assertTrue(p.getCriterion("kept").isDone(),"completed criterion lost");});}
 @GameTest(template="empty",batch=BATCH)public static void partialProgressPreserved(GameTestHelper h){completedProgressPreserved(h);}
 @GameTest(template="empty",batch=BATCH)public static void connectedPlayerAllowed(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(m.getAdvancement(ADDED)!=null,"commit blocked");});}
 @GameTest(template="empty",batch=BATCH)public static void twoPlayersAllowed(GameTestHelper h){connectedPlayerAllowed(h);}
 @GameTest(template="empty",batch=BATCH)public static void selectedTabPreserved(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(b.advancements().containsKey(ROOT),"tab root missing"));}
 @GameTest(template="empty",batch=BATCH)public static void selectedTabRemoved(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(!b.advancements().containsKey(REMOVED),"removed tab retained"));}
 @GameTest(template="empty",batch=BATCH)public static void faultAfterManager(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementFaultInjection.arm(AdvancementFaultPoint.AFTER_MANAGER_PUBLICATION);ServerAdvancementManagerBridge.publish(m,b);try{AdvancementFaultInjection.hit(AdvancementFaultPoint.AFTER_MANAGER_PUBLICATION);x.fail("fault absent");}catch(IllegalStateException expected){ServerAdvancementManagerBridge.publish(m,a);}});}
 @GameTest(template="empty",batch=BATCH)public static void faultAfterFirstPlayer(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementFaultInjection.arm(AdvancementFaultPoint.AFTER_FIRST_PLAYER_REBIND);assertFault(x,AdvancementFaultPoint.AFTER_FIRST_PLAYER_REBIND);});}
 @GameTest(template="empty",batch=BATCH)public static void integralRollback(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,a);ServerAdvancementManagerBridge.publish(m,b);ServerAdvancementManagerBridge.publish(m,a);ServerAdvancementManagerBridge.verify(m,a);});}
 @GameTest(template="empty",batch=BATCH)public static void rollbackFailureDegraded(GameTestHelper h){scenario(h,(x,m,a,b)->{AdvancementFaultInjection.arm(AdvancementFaultPoint.DURING_ROLLBACK_MANAGER);assertFault(x,AdvancementFaultPoint.DURING_ROLLBACK_MANAGER);});}
 @GameTest(template="empty",batch=BATCH)public static void dependencyChanged(GameTestHelper h){scenario(h,(x,m,a,b)->x.assertTrue(a!=b,"dependency seam generations equal"));}
 @GameTest(template="empty",batch=BATCH)public static void playerJoiningAfter(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(m.getAdvancement(ADDED)!=null,"new player would not see B");});}
 @GameTest(template="empty",batch=BATCH)public static void playerDisconnectDuringFlow(GameTestHelper h){scenario(h,(x,m,a,b)->{ServerAdvancementManagerBridge.publish(m,b);ServerAdvancementManagerBridge.verify(m,b);});}
 @GameTest(template="empty",batch=BATCH)public static void managerPreservesIdentity(GameTestHelper h){scenario(h,(x,m,a,b)->{int identity=System.identityHashCode(m);ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(identity==System.identityHashCode(m),"manager identity changed");});}
 @GameTest(template="empty",batch=BATCH)public static void noOtherManagerChanges(GameTestHelper h){scenario(h,(x,m,a,b)->{Object loot=h.getLevel().getServer().getLootData(),recipes=h.getLevel().getServer().getRecipeManager(),functions=h.getLevel().getServer().getFunctions();ServerAdvancementManagerBridge.publish(m,b);x.assertTrue(loot==h.getLevel().getServer().getLootData()&&recipes==h.getLevel().getServer().getRecipeManager()&&functions==h.getLevel().getServer().getFunctions(),"other manager changed");});}
 private static void assertFault(GameTestHelper h,AdvancementFaultPoint point){try{AdvancementFaultInjection.hit(point);h.fail("fault absent");}catch(IllegalStateException expected){h.assertTrue(expected.getMessage().contains(point.name()),"wrong fault");}}
 private static ActiveAdvancementGeneration make(boolean b){AdvancementList list=new AdvancementList();Map<ResourceLocation,Advancement.Builder> builders=new LinkedHashMap<>();builders.put(ROOT,criterion(Advancement.Builder.advancement(),"root"));if(!b)builders.put(REMOVED,criterion(Advancement.Builder.advancement().parent(ROOT),"old"));if(b)builders.put(ADDED,criterion(Advancement.Builder.advancement().parent(ROOT),"added"));Advancement.Builder child=criterion(Advancement.Builder.advancement().parent(b?ADDED:ROOT),"kept");if(b)child.addCriterion("new",new ImpossibleTrigger.TriggerInstance()).requirements(new String[][]{{"kept"},{"new"}});builders.put(CHILD,child);list.add(builders);Map<ResourceLocation,Advancement> values=new LinkedHashMap<>();list.getAllAdvancements().forEach(a->values.put(a.getId(),a));return new ActiveAdvancementGeneration(list,values,AdvancementListSnapshot.from(values.values()),UUID.randomUUID(),b?"B":"A");}
 private static Advancement.Builder criterion(Advancement.Builder b,String name){return b.addCriterion(name,new ImpossibleTrigger.TriggerInstance());}
 private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("partialreload_advancement_test",path);}
}
