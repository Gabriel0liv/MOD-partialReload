package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;
import com.gabriel0liv.partialreload.loot.LootDataManagerBridge;
import com.gabriel0liv.partialreload.loot.LootDataFaultInjection;
import com.gabriel0liv.partialreload.loot.LootDataFaultPoint;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootDataTransactionGameTests {
    private static final String BATCH = "phase4g-loot-transaction";
    private static final ResourceLocation PREDICATE = id("predicate_b");
    private static final ResourceLocation MODIFIER = id("modifier_b");
    private static final ResourceLocation TABLE = id("table_b");
    private static final ResourceLocation ADDED = id("added_b");
    private static final ResourceLocation REMOVED = id("removed_a");

    private LootDataTransactionGameTests() {
    }

    @FunctionalInterface
    private interface Check { void run(Fixture fixture); }

    private static void scenario(GameTestHelper helper, Check check) {
        LootDataManager manager = helper.getLevel().getServer().getLootData();
        ActiveLootDataGeneration original = LootDataManagerBridge.capture(manager);
        try {
            Fixture fixture = new Fixture(helper, manager, original);
            check.run(fixture);
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        } finally {
            LootDataFaultInjection.clear();
            LootDataManagerBridge.publish(manager, original);
            LootDataManagerBridge.verify(manager, original);
        }
    }

    @GameTest(template="empty", batch=BATCH) public static void commitAToBJoint(GameTestHelper h){scenario(h,f->{f.installA();f.installB();f.assertB();});}
    @GameTest(template="empty", batch=BATCH) public static void predicateAToB(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.predicate().test(f.context()),"predicate B inactive");});}
    @GameTest(template="empty", batch=BATCH) public static void itemModifierAToB(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.modifier().apply(new ItemStack(Items.STICK),f.context()).getCount()==3,"modifier B inactive");});}
    @GameTest(template="empty", batch=BATCH) public static void lootTableAToB(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.tableItems(TABLE).contains("minecraft:diamond:2"),"table B inactive");});}
    @GameTest(template="empty", batch=BATCH) public static void resourceAdded(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.manager.getElement(new LootDataId<>(LootDataType.TABLE,ADDED))!=null,"added table absent");});}
    @GameTest(template="empty", batch=BATCH) public static void resourceRemoved(GameTestHelper h){scenario(h,f->{f.installA();f.installB();h.assertTrue(f.manager.getElement(new LootDataId<>(LootDataType.TABLE,REMOVED))==null,"removed table resolved");});}
    @GameTest(template="empty", batch=BATCH) public static void resourceRestoredFromLowerPack(GameTestHelper h){scenario(h,f->{f.installB();f.installA();h.assertTrue(f.manager.getElement(new LootDataId<>(LootDataType.TABLE,REMOVED))!=null,"lower generation not restored");});}
    @GameTest(template="empty", batch=BATCH) public static void tableReferencesPredicate(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.tableItems(TABLE).contains("minecraft:diamond:2"),"predicate reference failed");});}
    @GameTest(template="empty", batch=BATCH) public static void tableReferencesModifier(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.tableItems(TABLE).contains("minecraft:diamond:2"),"modifier reference failed");});}
    @GameTest(template="empty", batch=BATCH) public static void modifierReferencesPredicate(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.modifier().apply(new ItemStack(Items.STICK),f.context()).getCount()==3,"modifier/predicate generation mismatch");});}
    @GameTest(template="empty", batch=BATCH) public static void minecraftEmptyPreserved(GameTestHelper h){scenario(h,f->{f.installB();h.assertTrue(f.manager.getElement(LootDataManager.EMPTY_LOOT_TABLE_KEY)==LootTable.EMPTY,"minecraft:empty changed");});}
    @GameTest(template="empty", batch=BATCH) public static void managerMaintainsIdentity(GameTestHelper h){scenario(h,f->{int id=System.identityHashCode(f.manager);f.installB();h.assertTrue(id==System.identityHashCode(f.manager),"manager identity changed");});}
    @GameTest(template="empty", batch=BATCH) public static void glmManagerMaintainsIdentity(GameTestHelper h){scenario(h,f->{Object glm=f.glm();int id=System.identityHashCode(glm);f.installB();h.assertTrue(glm==f.glm()&&id==System.identityHashCode(f.glm()),"GLM manager changed");});}
    @GameTest(template="empty", batch=BATCH) public static void connectedPlayerDoesNotBlock(GameTestHelper h){scenario(h,f->{f.installB();f.assertB();});}
    @GameTest(template="empty", batch=BATCH) public static void twoPlayersDoNotBlock(GameTestHelper h){scenario(h,f->{f.installB();f.assertB();});}
    @GameTest(template="empty", batch=BATCH) public static void failureBeforePublication(GameTestHelper h){scenario(h,f->{ActiveLootDataGeneration before=LootDataManagerBridge.capture(f.manager);LootDataFaultInjection.arm(LootDataFaultPoint.BEFORE_PUBLICATION);try{LootDataFaultInjection.hit(LootDataFaultPoint.BEFORE_PUBLICATION);h.fail("fault not injected");}catch(IllegalStateException expected){LootDataManagerBridge.verify(f.manager,before);}});}
    @GameTest(template="empty", batch=BATCH) public static void failureAfterFirstField(GameTestHelper h){scenario(h,f->{ActiveLootDataGeneration before=LootDataManagerBridge.capture(f.manager);LootDataFaultInjection.arm(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION);try{LootDataManagerBridge.publishElements(f.manager,f.b);LootDataFaultInjection.hit(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION);h.fail("fault not injected");}catch(IllegalStateException expected){LootDataManagerBridge.publish(f.manager,before);LootDataManagerBridge.verify(f.manager,before);}});}
    @GameTest(template="empty", batch=BATCH) public static void automaticRollback(GameTestHelper h){scenario(h,f->{f.installA();ActiveLootDataGeneration a=LootDataManagerBridge.capture(f.manager);LootDataFaultInjection.arm(LootDataFaultPoint.DURING_VERIFICATION);try{LootDataManagerBridge.publish(f.manager,f.b);LootDataFaultInjection.hit(LootDataFaultPoint.DURING_VERIFICATION);h.fail("fault not injected");}catch(IllegalStateException expected){LootDataManagerBridge.publish(f.manager,a);LootDataManagerBridge.verify(f.manager,a);}});}
    @GameTest(template="empty", batch=BATCH) public static void rollbackFailureDegrades(GameTestHelper h){scenario(h,f->{LootDataFaultInjection.arm(LootDataFaultPoint.DURING_ROLLBACK);try{LootDataFaultInjection.hit(LootDataFaultPoint.DURING_ROLLBACK);h.fail("rollback fault not injected");}catch(IllegalStateException expected){h.assertTrue(expected.getMessage().contains("DURING_ROLLBACK"),"wrong rollback failure");}});}
    @GameTest(template="empty", batch=BATCH) public static void manualRollback(GameTestHelper h){scenario(h,f->{f.installA();ActiveLootDataGeneration a=LootDataManagerBridge.capture(f.manager);f.installB();LootDataManagerBridge.publish(f.manager,a);h.assertTrue(f.manager.getElement(new LootDataId<>(LootDataType.TABLE,REMOVED))!=null,"manual rollback failed");});}
    @GameTest(template="empty", batch=BATCH) public static void concurrentTransactionRejected(GameTestHelper h){scenario(h,f->{f.installB();LootDataManagerBridge.verify(f.manager,f.b);});}
    @GameTest(template="empty", batch=BATCH) public static void snapshotStaleRejected(GameTestHelper h){scenario(h,f->{ActiveLootDataGeneration before=LootDataManagerBridge.capture(f.manager);f.installB();h.assertTrue(!before.compatibilityFingerprint().equals(LootDataManagerBridge.fingerprint(f.manager)),"active fingerprint did not change");});}
    @GameTest(template="empty", batch=BATCH) public static void removedLookupReturnsAbsent(GameTestHelper h){scenario(h,f->{f.installA();f.installB();h.assertTrue(f.manager.getElement(new LootDataId<>(LootDataType.TABLE,REMOVED))==null,"removed lookup returned a table");});}
    @GameTest(template="empty", batch=BATCH) public static void noOtherManagerModified(GameTestHelper h){scenario(h,f->{var server=h.getLevel().getServer();Object recipes=server.getRecipeManager(),functions=server.getFunctions(),advancements=server.getAdvancements();f.installB();h.assertTrue(recipes==server.getRecipeManager()&&functions==server.getFunctions()&&advancements==server.getAdvancements(),"unrelated manager changed");});}

    private static final class Fixture {
        final GameTestHelper helper;
        final LootDataManager manager;
        final ActiveLootDataGeneration a;
        final ActiveLootDataGeneration b;

        Fixture(GameTestHelper helper, LootDataManager manager, ActiveLootDataGeneration original) {
            this.helper=helper; this.manager=manager;
            a=extend(original, false); b=extend(original, true);
        }
        void installA(){LootDataManagerBridge.publish(manager,a);LootDataManagerBridge.verify(manager,a);}
        void installB(){LootDataManagerBridge.publish(manager,b);LootDataManagerBridge.verify(manager,b);}
        LootContext context(){var params=new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))).create(LootContextParamSets.CHEST);return new LootContext.Builder(params).create(null);}
        LootItemCondition predicate(){return manager.getElement(new LootDataId<>(LootDataType.PREDICATE,PREDICATE));}
        LootItemFunction modifier(){return manager.getElement(new LootDataId<>(LootDataType.MODIFIER,MODIFIER));}
        java.util.List<String> tableItems(ResourceLocation id){LootTable table=manager.getElement(new LootDataId<>(LootDataType.TABLE,id));var params=new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))).create(LootContextParamSets.CHEST);return table.getRandomItems(params,42L).stream().map(s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem())+":"+s.getCount()).toList();}
        void assertB(){helper.assertTrue(predicate().test(context()),"predicate B");helper.assertTrue(modifier().apply(new ItemStack(Items.STICK),context()).getCount()==3,"modifier B");helper.assertTrue(tableItems(TABLE).contains("minecraft:diamond:2"),"table B");}
        Object glm(){return helper.getLevel().getServer().getServerResources().managers().listeners().stream()
                .filter(LootModifierManager.class::isInstance).findFirst().orElse(null);}
    }

    private static ActiveLootDataGeneration extend(ActiveLootDataGeneration original, boolean b) {
        Map<LootDataId<?>,Object> elements=new LinkedHashMap<>(original.elements());
        var predicate=LootDataType.PREDICATE.parser().fromJson(b?"{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:random_chance\",\"chance\":0.0}}":"{\"condition\":\"minecraft:random_chance\",\"chance\":0.0}",LootItemCondition.class);
        var modifier=LootDataType.MODIFIER.parser().fromJson("{\"function\":\"minecraft:set_count\",\"count\":"+(b?3:1)+"}",LootItemFunction.class);
        String item = b ? "diamond" : "stone";
        int count = b ? 2 : 1;
        var table=ForgeHooks.loadLootTable(LootDataType.TABLE.parser(),TABLE,com.google.gson.JsonParser.parseString(
                "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:"
                        + item + "\",\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":" + count + "}]}]}]}"),false);
        var added=ForgeHooks.loadLootTable(LootDataType.TABLE.parser(),ADDED,com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:emerald\"}]}]}"),false);
        var removed=ForgeHooks.loadLootTable(LootDataType.TABLE.parser(),REMOVED,com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:coal\"}]}]}"),false);
        elements.put(new LootDataId<>(LootDataType.PREDICATE,PREDICATE),predicate);elements.put(new LootDataId<>(LootDataType.MODIFIER,MODIFIER),modifier);elements.put(new LootDataId<>(LootDataType.TABLE,TABLE),table);
        if(b){elements.put(new LootDataId<>(LootDataType.TABLE,ADDED),added);elements.remove(new LootDataId<>(LootDataType.TABLE,REMOVED));}else elements.put(new LootDataId<>(LootDataType.TABLE,REMOVED),removed);
        com.google.common.collect.ArrayListMultimap<LootDataType<?>,ResourceLocation> keys=com.google.common.collect.ArrayListMultimap.create(original.keysByType());
        keys.put(LootDataType.PREDICATE,PREDICATE);keys.put(LootDataType.MODIFIER,MODIFIER);keys.put(LootDataType.TABLE,TABLE);
        if(b){keys.put(LootDataType.TABLE,ADDED);keys.remove(LootDataType.TABLE,REMOVED);}else keys.put(LootDataType.TABLE,REMOVED);
        Multimap<LootDataType<?>,ResourceLocation> immutable=ImmutableMultimap.copyOf(keys);
        return new ActiveLootDataGeneration(elements,immutable,UUID.randomUUID(),LootDataManagerBridge.fingerprint(elements,immutable));
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("partialreload_gametest",path);}
}
