package com.gabriel0liv.partialreload.resource;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceScannerTest {
    @Test
    void classifiesAllPhaseOneMappings() {
        assertEquals(ReloadCategory.FUNCTIONS, ResourceScanner.classifyPath("functions/a/b.mcfunction"));
        assertEquals(ReloadCategory.ADVANCEMENTS, ResourceScanner.classifyPath("advancements/a.json"));
        assertEquals(ReloadCategory.PREDICATES, ResourceScanner.classifyPath("predicates/a.json"));
        assertEquals(ReloadCategory.RECIPES, ResourceScanner.classifyPath("recipes/a.json"));
        assertEquals(ReloadCategory.LOOT, ResourceScanner.classifyPath("loot_tables/chests/a.json"));
        assertEquals(ReloadCategory.ITEM_MODIFIERS, ResourceScanner.classifyPath("item_modifiers/a.json"));
        assertEquals(ReloadCategory.GLOBAL_LOOT_MODIFIERS,
                ResourceScanner.classifyPath("loot_modifiers/a.json"));
        assertEquals(ReloadCategory.FUNCTIONS, ResourceScanner.classifyPath("tags/functions/tick.json"));
        assertEquals(ReloadCategory.TAGS, ResourceScanner.classifyPath("tags/items/a.json"));
        assertEquals(ReloadCategory.ORIGINS, ResourceScanner.classifyPath("powers/a.json"));
        assertEquals(ReloadCategory.ORIGINS, ResourceScanner.classifyPath("origins/a.json"));
        assertEquals(ReloadCategory.ORIGINS, ResourceScanner.classifyPath("origin_layers/a.json"));
        assertEquals(ReloadCategory.ORIGINS, ResourceScanner.classifyPath("global_power_sets/a.json"));
        assertEquals(ReloadCategory.DYNAMIC_REGISTRIES, ResourceScanner.classifyPath("worldgen/biome/a.json"));
        assertEquals(ReloadCategory.DYNAMIC_REGISTRIES, ResourceScanner.classifyPath("damage_type/a.json"));
        assertEquals(ReloadCategory.SILENTGEAR, ResourceScanner.classifyPath("silentgear_materials/a.json"));
        assertEquals(ReloadCategory.SILENTGEAR, ResourceScanner.classifyPath("silentgear_traits/a.json"));
        assertEquals(ReloadCategory.UNKNOWN, ResourceScanner.classifyPath("custom_loader/a.json"));
        assertEquals(ReloadCategory.UNKNOWN, ResourceScanner.classifyPath("functions/not_a_function.json"));
    }

    @Test
    void createsStableSha256Fingerprint() {
        ResourceFingerprint fingerprint = ResourceFingerprint.sha256("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals(ResourceFingerprint.SHA_256, fingerprint.algorithm());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fingerprint.hash());
        assertEquals(3, fingerprint.size());
    }

    @Test
    void createsPublicLogicalResourceId() {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "drathoscore",
                "functions/totem_limit/tick.mcfunction"
        );

        assertEquals(
                ResourceLocation.fromNamespaceAndPath("drathoscore", "totem_limit/tick"),
                ResourceScanner.logicalId(location, ReloadCategory.FUNCTIONS)
        );
    }

    @Test
    void enumeratesOnlyValidNonEmptyServerDataRoots() {
        assertFalse(ResourceScanner.scanRoots().isEmpty());
        assertFalse(ResourceScanner.scanRoots().contains(""));
        assertEquals(ResourceScanner.scanRoots().size(), ResourceScanner.scanRoots().stream().distinct().count());
    }
}
