package com.gabriel0liv.partialreload.glm;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalLootModifierModelTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:a");
    private static final ResourceLocation B = ResourceLocation.parse("test:b");
    private static final ResourceLocation C = ResourceLocation.parse("test:c");

    @AfterEach void clearFault() { GlobalLootModifierFaultInjection.clear(); }

    @Test void stackWithoutReplaceAppendsInOrder() {
        var result = GlobalLootModifierStack.merge(List.of(
                new GlobalLootModifierStack.Layer("low", false, List.of(A, B)),
                new GlobalLootModifierStack.Layer("high", false, List.of(C))));
        assertEquals(List.of(A, B, C), result.orderedIds());
    }

    @Test void replaceClearsLowerPackEntries() {
        var result = GlobalLootModifierStack.merge(List.of(
                new GlobalLootModifierStack.Layer("low", false, List.of(A, B)),
                new GlobalLootModifierStack.Layer("high", true, List.of(C))));
        assertEquals(List.of(C), result.orderedIds());
    }

    @Test void repeatedEntryMovesToEndAndUpdatesSource() {
        var result = GlobalLootModifierStack.merge(List.of(
                new GlobalLootModifierStack.Layer("low", false, List.of(A, B)),
                new GlobalLootModifierStack.Layer("high", false, List.of(A))));
        assertEquals(List.of(B, A), result.orderedIds());
        assertEquals("high", result.sourcePacks().get(A));
    }

    @Test void activeGenerationPreservesOrderAndIsImmutable() {
        LinkedHashMap<ResourceLocation, IGlobalLootModifier> values = new LinkedHashMap<>();
        values.put(B, new Modifier()); values.put(A, new Modifier());
        var generation = new ActiveGlobalLootModifierGeneration(values, UUID.randomUUID(), "digest");
        assertEquals(List.of(B, A), List.copyOf(generation.orderedModifiers().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> generation.orderedModifiers().put(C, new Modifier()));
    }

    @Test void deltaCountsOrderedChangeKinds() {
        var delta = new GlobalLootModifierDelta(List.of(A), List.of(B), List.of(C),
                List.of(A), List.of(), List.of(B));
        assertEquals(4, delta.changedCount());
    }

    @Test void unchangedContentWithDifferentPositionIsMoved() {
        assertEquals(List.of(B, A), GlobalLootModifierDelta.movedIds(
                List.of(A, B, C), List.of(B, A, C), java.util.Set.of(A, B, C)));
        assertEquals(List.of(), GlobalLootModifierDelta.movedIds(
                List.of(A, B), List.of(B, A), java.util.Set.of()));
    }

    @Test void faultInjectionIsOneShot() {
        GlobalLootModifierFaultInjection.arm(GlobalLootModifierFaultPoint.AFTER_GLM_PUBLICATION);
        assertThrows(IllegalStateException.class, () -> GlobalLootModifierFaultInjection.hit(
                GlobalLootModifierFaultPoint.AFTER_GLM_PUBLICATION));
        assertDoesNotThrow(() -> GlobalLootModifierFaultInjection.hit(
                GlobalLootModifierFaultPoint.AFTER_GLM_PUBLICATION));
    }

    @Test void transactionRetainsExactExpectedGeneration() {
        var expected = new ActiveGlobalLootModifierGeneration(Map.of(), UUID.randomUUID(), "d");
        var tx = new GlobalLootModifierCommitTransaction(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                "test", 42, expected);
        assertSame(expected, tx.expectedActiveGeneration());
        assertEquals(42, tx.managerIdentity());
    }

    @Test void jointTransactionRetainsBothExactExpectedGenerations() {
        var loot = new com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration(
                Map.of(), com.google.common.collect.ImmutableMultimap.of(), UUID.randomUUID(), "loot");
        var glm = new ActiveGlobalLootModifierGeneration(Map.of(), UUID.randomUUID(), "glm");
        var tx = new LootAndGlobalModifiersCommitTransaction(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                "test", 1, 2, loot, glm);
        assertSame(loot, tx.expectedLootGeneration());
        assertSame(glm, tx.expectedGlmGeneration());
    }

    private static final class Modifier implements IGlobalLootModifier {
        @Override public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            return generatedLoot;
        }
        @Override public Codec<? extends IGlobalLootModifier> codec() { return Codec.unit(this); }
    }
}
