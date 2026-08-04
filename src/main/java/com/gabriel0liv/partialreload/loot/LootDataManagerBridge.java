package com.gabriel0liv.partialreload.loot;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Exact Minecraft 1.20.1 bridge. Field access is provided only by the AT. */
public final class LootDataManagerBridge {
    private LootDataManagerBridge() {
    }

    public static void validateLayout(LootDataManager manager) {
        Objects.requireNonNull(manager, "manager");
        if (manager.getClass() != LootDataManager.class
                || !(manager.elements instanceof Map<?, ?>)
                || !(manager.typeKeys instanceof Multimap<?, ?>)) {
            throw new IllegalStateException("LOOT_DATA_MANAGER_LAYOUT_UNSUPPORTED: runtimeClass="
                    + manager.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    public static ActiveLootDataGeneration capture(LootDataManager manager) {
        validateLayout(manager);
        Map<LootDataId<?>, Object> elements = new LinkedHashMap<>();
        ((Map<LootDataId<?>, ?>) manager.elements).forEach(elements::put);
        Multimap<LootDataType<?>, ResourceLocation> keys =
                ImmutableMultimap.copyOf((Multimap<LootDataType<?>, ResourceLocation>) manager.typeKeys);
        validateComplete(elements, keys);
        return generation(elements, keys);
    }

    public static ActiveLootDataGeneration fromPrepared(PreparedLootData prepared) {
        Objects.requireNonNull(prepared, "prepared");
        Map<LootDataId<?>, Object> elements = new LinkedHashMap<>();
        prepared.predicates().forEach((id, value) ->
                elements.put(new LootDataId<>(LootDataType.PREDICATE, id), value.candidate()));
        prepared.itemModifiers().forEach((id, value) ->
                elements.put(new LootDataId<>(LootDataType.MODIFIER, id), value.candidate()));
        prepared.lootTables().forEach((id, value) ->
                elements.put(new LootDataId<>(LootDataType.TABLE, id), value.candidate()));
        elements.put(LootDataManager.EMPTY_LOOT_TABLE_KEY, LootTable.EMPTY);

        ImmutableMultimap.Builder<LootDataType<?>, ResourceLocation> keys = ImmutableMultimap.builder();
        prepared.predicates().keySet().forEach(id -> keys.put(LootDataType.PREDICATE, id));
        prepared.itemModifiers().keySet().forEach(id -> keys.put(LootDataType.MODIFIER, id));
        prepared.lootTables().keySet().stream()
                .filter(id -> !id.equals(LootDataManager.EMPTY_LOOT_TABLE_KEY.location()))
                .forEach(id -> keys.put(LootDataType.TABLE, id));
        ActiveLootDataGeneration result = generation(elements, keys.build());
        validateComplete(result.elements(), result.keysByType());
        return result;
    }

    public static void publishElements(LootDataManager manager, ActiveLootDataGeneration generation) {
        validateLayout(manager);
        validateComplete(generation.elements(), generation.keysByType());
        manager.elements = Map.copyOf(generation.elements());
    }

    public static void publishTypeIndex(LootDataManager manager, ActiveLootDataGeneration generation) {
        validateLayout(manager);
        validateComplete(generation.elements(), generation.keysByType());
        manager.typeKeys = ImmutableMultimap.copyOf(generation.keysByType());
    }

    public static void publish(LootDataManager manager, ActiveLootDataGeneration generation) {
        publishElements(manager, generation);
        publishTypeIndex(manager, generation);
    }

    public static void verify(LootDataManager manager, ActiveLootDataGeneration expected) {
        validateLayout(manager);
        validateComplete(expected.elements(), expected.keysByType());
        for (Map.Entry<LootDataId<?>, Object> entry : expected.elements().entrySet()) {
            if (get(manager, entry.getKey()) != entry.getValue()) {
                throw new IllegalStateException("LOOT_COMMIT_VERIFICATION_FAILED: lookup=" + entry.getKey());
            }
        }
        for (LootDataType<?> type : List.of(LootDataType.PREDICATE, LootDataType.MODIFIER, LootDataType.TABLE)) {
            if (!SetSupport.copy(manager.getKeys(type)).equals(SetSupport.copy(expected.keysByType().get(type)))) {
                throw new IllegalStateException("LOOT_COMMIT_VERIFICATION_FAILED: keys=" + type.directory());
            }
        }
        if (manager.getElement(LootDataManager.EMPTY_LOOT_TABLE_KEY) != LootTable.EMPTY) {
            throw new IllegalStateException("LOOT_COMMIT_VERIFICATION_FAILED: minecraft:empty");
        }
        ActiveLootDataGeneration observed = capture(manager);
        if (!observed.compatibilityFingerprint().equals(expected.compatibilityFingerprint())) {
            throw new IllegalStateException("LOOT_COMMIT_VERIFICATION_FAILED: fingerprint");
        }
    }

    public static String fingerprint(LootDataManager manager) {
        return capture(manager).compatibilityFingerprint();
    }

    /**
     * Exact TOCTOU guard. The diagnostic fingerprint is deliberately not used:
     * every logical ID must still resolve to the very same object reference and
     * the complete per-type key index must be unchanged.
     */
    public static boolean matchesExactly(LootDataManager manager, ActiveLootDataGeneration expected) {
        Objects.requireNonNull(expected, "expected");
        validateLayout(manager);
        validateComplete(expected.elements(), expected.keysByType());
        @SuppressWarnings("unchecked")
        Map<LootDataId<?>, Object> actual = (Map<LootDataId<?>, Object>) manager.elements;
        return sameGenerationReferences(expected.elements(), expected.keysByType(), actual,
                ImmutableMultimap.copyOf(manager.typeKeys))
                && actual.get(LootDataManager.EMPTY_LOOT_TABLE_KEY) == LootTable.EMPTY;
    }

    public static boolean sameGenerationReferences(
            Map<LootDataId<?>, Object> expectedElements,
            Multimap<LootDataType<?>, ResourceLocation> expectedKeys,
            Map<LootDataId<?>, Object> actualElements,
            Multimap<LootDataType<?>, ResourceLocation> actualKeys) {
        if (!actualElements.keySet().equals(expectedElements.keySet()) || !actualKeys.equals(expectedKeys)) return false;
        for (Map.Entry<LootDataId<?>, Object> entry : expectedElements.entrySet()) {
            if (actualElements.get(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    private static ActiveLootDataGeneration generation(
            Map<LootDataId<?>, Object> elements,
            Multimap<LootDataType<?>, ResourceLocation> keys
    ) {
        Map<LootDataId<?>, Object> immutableElements = Map.copyOf(elements);
        Multimap<LootDataType<?>, ResourceLocation> immutableKeys = ImmutableMultimap.copyOf(keys);
        return new ActiveLootDataGeneration(immutableElements, immutableKeys, UUID.randomUUID(),
                fingerprint(immutableElements, immutableKeys));
    }

    public static String fingerprint(
            Map<LootDataId<?>, Object> elements,
            Multimap<LootDataType<?>, ResourceLocation> keys
    ) {
        List<String> lines = new ArrayList<>();
        elements.forEach((id, value) -> lines.add(typeName(id.type()) + ":" + id.location()
                + ":" + value.getClass().getName() + ":" + System.identityHashCode(value)));
        keys.entries().forEach(entry -> lines.add("key:" + typeName(entry.getKey()) + ":" + entry.getValue()));
        lines.sort(String::compareTo);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.join("\n", lines)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void validateComplete(
            Map<LootDataId<?>, Object> elements,
            Multimap<LootDataType<?>, ResourceLocation> keys
    ) {
        if (elements.get(LootDataManager.EMPTY_LOOT_TABLE_KEY) != LootTable.EMPTY) {
            throw new IllegalStateException("LOOT_COMMIT_CANDIDATE_INCOMPLETE: minecraft:empty");
        }
        for (Map.Entry<LootDataType<?>, ResourceLocation> entry : keys.entries()) {
            LootDataId<?> id = new LootDataId<>(entry.getKey(), entry.getValue());
            if (!elements.containsKey(id)) {
                throw new IllegalStateException("LOOT_COMMIT_CANDIDATE_INCOMPLETE: orphan key=" + id);
            }
        }
        for (LootDataId<?> id : elements.keySet()) {
            if (!id.equals(LootDataManager.EMPTY_LOOT_TABLE_KEY)
                    && !keys.containsEntry(id.type(), id.location())) {
                throw new IllegalStateException("LOOT_COMMIT_CANDIDATE_INCOMPLETE: missing key=" + id);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object get(LootDataManager manager, LootDataId<?> id) {
        return manager.getElement((LootDataId) id);
    }

    private static String typeName(LootDataType<?> type) {
        if (type == LootDataType.PREDICATE) return "predicate";
        if (type == LootDataType.MODIFIER) return "modifier";
        if (type == LootDataType.TABLE) return "table";
        return "unsupported:" + type.directory();
    }

    private static final class SetSupport {
        private static java.util.Set<ResourceLocation> copy(Iterable<ResourceLocation> values) {
            java.util.Set<ResourceLocation> result = new java.util.LinkedHashSet<>();
            values.forEach(result::add);
            return java.util.Set.copyOf(result);
        }
    }
}
