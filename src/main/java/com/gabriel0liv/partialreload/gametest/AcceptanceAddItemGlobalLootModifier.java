package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Userdev-only codec used by dedicated 4H acceptance fixtures; excluded from the production JAR. */
public final class AcceptanceAddItemGlobalLootModifier extends LootModifier {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, PartialReloadMod.MOD_ID);
    public static final Codec<AcceptanceAddItemGlobalLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).and(ResourceLocation.CODEC.fieldOf("item")
                    .forGetter(value -> value.item)).apply(instance, AcceptanceAddItemGlobalLootModifier::new));
    static { REGISTRY.register("acceptance_add_item", () -> CODEC); }
    private final ResourceLocation item;

    private AcceptanceAddItemGlobalLootModifier(
            net.minecraft.world.level.storage.loot.predicates.LootItemCondition[] conditions,
            ResourceLocation item) {
        super(conditions); this.item = item;
    }

    public static void register(IEventBus bus) { REGISTRY.register(bus); }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        generatedLoot.add(new ItemStack(BuiltInRegistries.ITEM.get(item)));
        return generatedLoot;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
