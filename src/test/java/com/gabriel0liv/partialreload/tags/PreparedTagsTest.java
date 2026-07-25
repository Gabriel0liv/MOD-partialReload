package com.gabriel0liv.partialreload.tags;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PreparedTagsTest {
    @Test
    void candidateIsImmutableAndDoesNotExposeBindings() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");
        PreparedTag tag = new PreparedTag("items", ResourceLocation.fromNamespaceAndPath("test", "mine"),
                "tags/items/mine.json", List.of("pack"), List.of("hash"), false,
                List.of("minecraft:stone"), List.of(), Set.of(), Set.of());
        PreparedTags prepared = new PreparedTags(UUID.randomUUID(), Instant.now(), new ResourceSnapshot(Instant.now(), Map.of()),
                Map.of("items", new PreparedRegistryTags("items", Map.of(tag.id(), tag), 1)),
                new TagDependencyGraph(Map.of(tag.id(), Set.of())),
                new TagDelta(Set.of(tag.id()), Set.of(), Set.of(), Set.of("minecraft:stone"), Set.of(), Set.of(), false, false),
                ValidationReport.VALID, 1, 1, 1, Set.of("items"), Set.of());
        assertTrue(prepared.isApplicable());
        assertEquals(1, prepared.resolvedMembers());
        assertThrows(UnsupportedOperationException.class, () -> prepared.registries().clear());
        assertThrows(UnsupportedOperationException.class, () -> prepared.registries().get("items").tags().clear());
    }
}
