package com.gabriel0liv.partialreload.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LootGraphExtractor {
    List<LootDependency> extract(LootDataKind sourceKind, ResourceLocation source, JsonElement json) {
        List<LootDependency> result = new ArrayList<>();
        walk(sourceKind, source, json, "$", result);
        return result;
    }

    private void walk(
            LootDataKind sourceKind,
            ResourceLocation source,
            JsonElement element,
            String path,
            List<LootDependency> result
    ) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                walk(sourceKind, source, array.get(index), path + "[" + index + "]", result);
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        reference(sourceKind, source, object, path, "condition", "minecraft:reference", "name",
                LootDataKind.PREDICATE, LootDependencyType.PREDICATE_REFERENCE, result);
        reference(sourceKind, source, object, path, "function", "minecraft:reference", "name",
                LootDataKind.ITEM_MODIFIER, LootDependencyType.ITEM_MODIFIER_REFERENCE, result);
        reference(sourceKind, source, object, path, "type", "minecraft:loot_table", "name",
                LootDataKind.LOOT_TABLE, LootDependencyType.LOOT_TABLE_REFERENCE, result);
        reference(sourceKind, source, object, path, "type", "minecraft:dynamic", "name",
                LootDataKind.LOOT_TABLE, LootDependencyType.DYNAMIC_DROP_REFERENCE, result);

        if (object.has("conditions") && object.get("conditions").isJsonArray()) {
            self(sourceKind, source, LootDependencyType.NESTED_CONDITION, path + ".conditions", result);
        }
        if (object.has("functions") && object.get("functions").isJsonArray()) {
            self(sourceKind, source, LootDependencyType.NESTED_FUNCTION, path + ".functions", result);
        }
        if (object.has("type") && object.get("type").isJsonPrimitive()) {
            String type = object.get("type").getAsString();
            if (type.equals("minecraft:alternatives") || type.equals("minecraft:group")
                    || type.equals("minecraft:sequence")) {
                self(sourceKind, source, LootDependencyType.COMPOSITE_ENTRY, path, result);
            }
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            walk(sourceKind, source, entry.getValue(), path + "." + entry.getKey(), result);
        }
    }

    private static void reference(
            LootDataKind sourceKind,
            ResourceLocation source,
            JsonObject object,
            String path,
            String discriminator,
            String expected,
            String targetField,
            LootDataKind targetKind,
            LootDependencyType type,
            List<LootDependency> result
    ) {
        if (!object.has(discriminator) || !object.has(targetField)
                || !object.get(discriminator).isJsonPrimitive()
                || !expected.equals(object.get(discriminator).getAsString())
                || !object.get(targetField).isJsonPrimitive()) return;
        ResourceLocation target = ResourceLocation.tryParse(object.get(targetField).getAsString());
        if (target != null) {
            result.add(new LootDependency(
                    sourceKind, source, targetKind, target, type, path + "." + targetField
            ));
        }
    }

    private static void self(
            LootDataKind kind,
            ResourceLocation source,
            LootDependencyType type,
            String path,
            List<LootDependency> result
    ) {
        result.add(new LootDependency(kind, source, kind, source, type, path));
    }
}

