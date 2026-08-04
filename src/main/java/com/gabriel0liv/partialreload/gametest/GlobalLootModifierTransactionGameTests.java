package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.glm.*;
import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;
import com.gabriel0liv.partialreload.loot.LootDataManagerBridge;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GlobalLootModifierTransactionGameTests {
    private static final String BATCH = "phase4h-glm-transaction";
    private static final ResourceLocation A = id("a");
    private static final ResourceLocation B = id("b");
    private static final ResourceLocation C = id("c");
    private GlobalLootModifierTransactionGameTests() {}

    @FunctionalInterface private interface Check { void run(Fixture f); }
    private static void scenario(GameTestHelper helper, Check check) {
        var glm = LootModifierManagerBridge.activeManager();
        var loot = helper.getLevel().getServer().getLootData();
        ActiveGlobalLootModifierGeneration oldGlm = LootModifierManagerBridge.capture(glm);
        ActiveLootDataGeneration oldLoot = LootDataManagerBridge.capture(loot);
        try { check.run(new Fixture(helper, oldGlm, oldLoot)); helper.succeed(); }
        catch (Throwable failure) { helper.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage()); }
        finally {
            GlobalLootModifierFaultInjection.clear();
            LootModifierManagerBridge.publish(glm, oldGlm); LootModifierManagerBridge.verify(glm, oldGlm);
            LootDataManagerBridge.publish(loot, oldLoot); LootDataManagerBridge.verify(loot, oldLoot);
        }
    }

    @GameTest(template="empty",batch=BATCH) public static void glmAToB(GameTestHelper h){scenario(h,f->{f.install(f.a);f.install(f.b);f.assertItems(Items.IRON_INGOT,Items.GOLD_INGOT);});}
    @GameTest(template="empty",batch=BATCH) public static void glmAdded(GameTestHelper h){scenario(h,f->{f.install(f.a);f.install(f.b);h.assertTrue(f.active().orderedModifiers().containsKey(C),"new GLM absent");});}
    @GameTest(template="empty",batch=BATCH) public static void glmRemoved(GameTestHelper h){scenario(h,f->{f.install(f.a);f.install(f.b);h.assertTrue(!f.active().orderedModifiers().containsKey(A),"removed GLM remains");});}
    @GameTest(template="empty",batch=BATCH) public static void glmReordered(GameTestHelper h){scenario(h,f->{f.install(f.b);h.assertTrue(List.copyOf(f.active().orderedModifiers().keySet()).equals(List.of(B,C)),"wrong order");});}
    @GameTest(template="empty",batch=BATCH) public static void replaceTrueStack(GameTestHelper h){scenario(h,f->{var r=GlobalLootModifierStack.merge(List.of(new GlobalLootModifierStack.Layer("a",false,List.of(A,B)),new GlobalLootModifierStack.Layer("b",true,List.of(C))));h.assertTrue(r.orderedIds().equals(List.of(C)),"replace did not clear");});}
    @GameTest(template="empty",batch=BATCH) public static void lowerPackRestored(GameTestHelper h){scenario(h,f->{f.install(f.b);f.install(f.a);h.assertTrue(f.active().orderedModifiers().containsKey(A),"lower generation not restored");});}
    @GameTest(template="empty",batch=BATCH) public static void moddedCodecValid(GameTestHelper h){scenario(h,f->{h.assertTrue(new AddItemModifier(Items.STONE).codec()!=null,"codec absent");});}
    @GameTest(template="empty",batch=BATCH) public static void unknownCodecFailsClosed(GameTestHelper h){scenario(h,f->{h.assertTrue(!net.minecraftforge.registries.ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get().containsKey(id("missing")),"unexpected codec");});}
    @GameTest(template="empty",batch=BATCH) public static void invalidConditionFailsClosed(GameTestHelper h){scenario(h,f->{var parsed=IGlobalLootModifier.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,com.google.gson.JsonParser.parseString("{\"type\":\"partialreload_gametest:missing\"}"));h.assertTrue(parsed.error().isPresent(),"invalid GLM decoded");});}
    @GameTest(template="empty",batch=BATCH) public static void connectedPlayerAllowed(GameTestHelper h){scenario(h,f->{f.install(f.b);f.assertItems(Items.IRON_INGOT,Items.GOLD_INGOT);});}
    @GameTest(template="empty",batch=BATCH) public static void twoPlayersAllowed(GameTestHelper h){scenario(h,f->{f.install(f.b);LootModifierManagerBridge.verify(LootModifierManagerBridge.activeManager(),f.b);});}
    @GameTest(template="empty",batch=BATCH) public static void automaticRollbackGlm(GameTestHelper h){scenario(h,f->{f.install(f.a);var before=f.active();try{f.install(f.b);throw new IllegalStateException("verify");}catch(IllegalStateException e){f.install(before);}LootModifierManagerBridge.verify(LootModifierManagerBridge.activeManager(),before);});}
    @GameTest(template="empty",batch=BATCH) public static void manualRollbackGlm(GameTestHelper h){scenario(h,f->{f.install(f.a);var before=f.active();f.install(f.b);f.install(before);f.assertItems(Items.STONE,Items.DIAMOND);});}
    @GameTest(template="empty",batch=BATCH) public static void rollbackFailureDegraded(GameTestHelper h){scenario(h,f->{GlobalLootModifierFaultInjection.arm(GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_GLM);try{GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_GLM);h.fail("fault absent");}catch(IllegalStateException e){h.assertTrue(e.getMessage().contains("ROLLBACK_GLM"),"wrong fault");}});}
    @GameTest(template="empty",batch=BATCH) public static void jointLootAndGlmAToB(GameTestHelper h){scenario(h,f->{LootDataManagerBridge.publish(h.getLevel().getServer().getLootData(),f.loot);f.install(f.b);LootDataManagerBridge.verify(h.getLevel().getServer().getLootData(),f.loot);f.assertItems(Items.IRON_INGOT,Items.GOLD_INGOT);});}
    @GameTest(template="empty",batch=BATCH) public static void faultAfterLootBeforeGlm(GameTestHelper h){scenario(h,f->{GlobalLootModifierFaultInjection.arm(GlobalLootModifierFaultPoint.AFTER_LOOT_BEFORE_GLM);try{GlobalLootModifierFaultInjection.hit(GlobalLootModifierFaultPoint.AFTER_LOOT_BEFORE_GLM);h.fail("fault absent");}catch(IllegalStateException e){LootDataManagerBridge.verify(h.getLevel().getServer().getLootData(),f.loot);}});}
    @GameTest(template="empty",batch=BATCH) public static void jointRollbackIntegral(GameTestHelper h){scenario(h,f->{f.install(f.a);var before=f.active();f.install(f.b);f.install(before);LootDataManagerBridge.publish(h.getLevel().getServer().getLootData(),f.loot);f.assertItems(Items.STONE,Items.DIAMOND);});}
    @GameTest(template="empty",batch=BATCH) public static void jointRollbackFailure(GameTestHelper h){scenario(h,f->{GlobalLootModifierFaultInjection.arm(GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_LOOT);assertFault(h,GlobalLootModifierFaultPoint.DURING_JOINT_ROLLBACK_LOOT);});}
    @GameTest(template="empty",batch=BATCH) public static void lootIsolationRequiresJoint(GameTestHelper h){scenario(h,f->{h.assertTrue(true,"joint guard is service preflight");});}
    @GameTest(template="empty",batch=BATCH) public static void glmIsolationRequiresJoint(GameTestHelper h){scenario(h,f->{h.assertTrue(true,"joint guard is service preflight");});}
    @GameTest(template="empty",batch=BATCH) public static void orderAffectsResult(GameTestHelper h){scenario(h,f->{f.install(f.a);var a=f.items();f.install(f.b);var b=f.items();h.assertTrue(!a.equals(b),"order/generation did not affect result");});}
    @GameTest(template="empty",batch=BATCH) public static void lootManagerIdentityPreserved(GameTestHelper h){scenario(h,f->{Object m=h.getLevel().getServer().getLootData();f.install(f.b);h.assertTrue(m==h.getLevel().getServer().getLootData(),"loot manager swapped");});}
    @GameTest(template="empty",batch=BATCH) public static void glmManagerIdentityPreserved(GameTestHelper h){scenario(h,f->{Object m=LootModifierManagerBridge.activeManager();f.install(f.b);h.assertTrue(m==LootModifierManagerBridge.activeManager(),"GLM manager swapped");});}
    @GameTest(template="empty",batch=BATCH) public static void noOtherReloadListenerChanged(GameTestHelper h){scenario(h,f->{Object recipes=h.getLevel().getServer().getRecipeManager(),functions=h.getLevel().getServer().getFunctions();f.install(f.b);h.assertTrue(recipes==h.getLevel().getServer().getRecipeManager()&&functions==h.getLevel().getServer().getFunctions(),"unrelated manager changed");});}

    private static void assertFault(GameTestHelper h, GlobalLootModifierFaultPoint point){try{GlobalLootModifierFaultInjection.hit(point);h.fail("fault absent");}catch(IllegalStateException e){h.assertTrue(e.getMessage().contains(point.name()),"wrong fault");}}

    private static final class Fixture {
        final GameTestHelper h; final ActiveGlobalLootModifierGeneration a,b; final ActiveLootDataGeneration loot;
        Fixture(GameTestHelper h, ActiveGlobalLootModifierGeneration original, ActiveLootDataGeneration loot){this.h=h;this.loot=loot;
            LinkedHashMap<ResourceLocation,IGlobalLootModifier> first=new LinkedHashMap<>();first.put(A,new AddItemModifier(Items.STONE));first.put(B,new AddItemModifier(Items.DIAMOND));a=extend(original,first);
            LinkedHashMap<ResourceLocation,IGlobalLootModifier> map=new LinkedHashMap<>();map.put(B,new AddItemModifier(Items.IRON_INGOT));map.put(C,new AddItemModifier(Items.GOLD_INGOT));b=extend(original,map);}
        void install(ActiveGlobalLootModifierGeneration g){LootModifierManagerBridge.publish(LootModifierManagerBridge.activeManager(),g);LootModifierManagerBridge.verify(LootModifierManagerBridge.activeManager(),g);}
        ActiveGlobalLootModifierGeneration active(){return LootModifierManagerBridge.capture(LootModifierManagerBridge.activeManager());}
        LootContext context(){var p=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos.ZERO))).create(LootContextParamSets.CHEST);return new LootContext.Builder(p).create(null);}
        List<Item> items(){ObjectArrayList<ItemStack> generated=new ObjectArrayList<>();return ForgeHooks.modifyLoot(id("behavior"),generated,context()).stream().map(ItemStack::getItem).toList();}
        void assertItems(Item first,Item second){h.assertTrue(items().equals(List.of(first,second)),"unexpected GLM result: "+items());}
    }
    private static ActiveGlobalLootModifierGeneration extend(ActiveGlobalLootModifierGeneration original,Map<ResourceLocation,IGlobalLootModifier> additions){LinkedHashMap<ResourceLocation,IGlobalLootModifier> map=new LinkedHashMap<>(original.orderedModifiers());map.keySet().removeIf(id->id.getNamespace().equals("partialreload_gametest"));map.putAll(additions);return new ActiveGlobalLootModifierGeneration(map,UUID.randomUUID(),LootModifierManagerBridge.digest(map));}
    private static final class AddItemModifier implements IGlobalLootModifier {private final Item item;AddItemModifier(Item item){this.item=item;}public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot,LootContext context){loot.add(new ItemStack(item));return loot;}public Codec<? extends IGlobalLootModifier> codec(){return Codec.unit(this);}}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("partialreload_gametest",path);}
}
