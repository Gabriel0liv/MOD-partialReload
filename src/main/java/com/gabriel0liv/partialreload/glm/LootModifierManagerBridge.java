package com.gabriel0liv.partialreload.glm;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeInternalHandler;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifierManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact Forge 47.4.10 bridge. Visibility is supplied only by the project AT. */
public final class LootModifierManagerBridge {
    private static final Access ACCESS = Access.resolve();
    private LootModifierManagerBridge() {}

    public static LootModifierManager activeManager() {
        LootModifierManager manager = ACCESS.activeManager();
        validateLayout(manager);
        return manager;
    }

    public static void validateLayout(LootModifierManager manager) {
        if (manager == null || manager.getClass() != LootModifierManager.class
                || !(ACCESS.read(manager) instanceof Map<?, ?>)) {
            throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: runtimeClass="
                    + (manager == null ? "null" : manager.getClass().getName()));
        }
    }

    public static ActiveGlobalLootModifierGeneration capture(LootModifierManager manager) {
        validateLayout(manager);
        return generation(ACCESS.read(manager));
    }

    public static ActiveGlobalLootModifierGeneration fromPrepared(PreparedGlobalLootModifiers prepared) {
        if (!prepared.isApplicable()) throw new IllegalStateException("GLM_COMMIT_ARTIFACT_INVALID");
        return generation(prepared.modifiers());
    }

    public static void publish(LootModifierManager manager,
                               ActiveGlobalLootModifierGeneration generation) {
        validateLayout(manager);
        ACCESS.write(manager, Collections.unmodifiableMap(
                new LinkedHashMap<>(generation.orderedModifiers())));
    }

    public static boolean matchesExactly(LootModifierManager manager,
                                         ActiveGlobalLootModifierGeneration expected) {
        validateLayout(manager);
        Map<ResourceLocation, IGlobalLootModifier> actual = ACCESS.read(manager);
        if (!List.copyOf(actual.keySet()).equals(List.copyOf(expected.orderedModifiers().keySet()))) return false;
        for (Map.Entry<ResourceLocation, IGlobalLootModifier> entry : expected.orderedModifiers().entrySet()) {
            if (actual.get(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    public static void verify(LootModifierManager manager,
                              ActiveGlobalLootModifierGeneration expected) {
        if (!matchesExactly(manager, expected)) {
            throw new IllegalStateException("GLM_COMMIT_VERIFICATION_FAILED");
        }
        if (!List.copyOf(manager.getAllLootMods())
                .equals(List.copyOf(expected.orderedModifiers().values()))) {
            throw new IllegalStateException("GLM_COMMIT_VERIFICATION_FAILED: public values");
        }
    }

    private static ActiveGlobalLootModifierGeneration generation(
            Map<ResourceLocation, IGlobalLootModifier> modifiers) {
        Map<ResourceLocation, IGlobalLootModifier> copy = Collections.unmodifiableMap(
                new LinkedHashMap<>(modifiers));
        return new ActiveGlobalLootModifierGeneration(copy, UUID.randomUUID(), digest(copy));
    }

    public static String digest(Map<ResourceLocation, IGlobalLootModifier> modifiers) {
        StringBuilder value = new StringBuilder();
        modifiers.forEach((id, modifier) -> value.append(id).append('|')
                .append(modifier.getClass().getName()).append('|')
                .append(System.identityHashCode(modifier)).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Access(Method managerMethod, Field modifiersField) {
        private static Access resolve() {
            try {
                Method method = ForgeInternalHandler.class.getMethod("getLootModifierManager");
                Field field = LootModifierManager.class.getField("registeredLootModifiers");
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != LootModifierManager.class
                        || field.getDeclaringClass() != LootModifierManager.class
                        || !Map.class.isAssignableFrom(field.getType())) {
                    throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: public contract mismatch");
                }
                return new Access(method, field);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: public AT contract", error);
            }
        }

        private LootModifierManager activeManager() {
            try {
                Object value = managerMethod.invoke(null);
                if (!(value instanceof LootModifierManager manager)) {
                    throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: manager value");
                }
                return manager;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: manager access", error);
            }
        }

        @SuppressWarnings("unchecked")
        private Map<ResourceLocation, IGlobalLootModifier> read(LootModifierManager manager) {
            try {
                Object value = modifiersField.get(manager);
                if (!(value instanceof Map<?, ?>)) {
                    throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: modifiers value");
                }
                return (Map<ResourceLocation, IGlobalLootModifier>) value;
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: modifiers read", error);
            }
        }

        private void write(LootModifierManager manager,
                           Map<ResourceLocation, IGlobalLootModifier> modifiers) {
            try {
                modifiersField.set(manager, modifiers);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED: modifiers write", error);
            }
        }
    }
}
