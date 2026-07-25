package com.gabriel0liv.partialreload.joint;

import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.recipe.RecipeDependencyGraph;
import com.gabriel0liv.partialreload.recipe.RecipeDelta;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.tags.*;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JointPreparationTest {
    @Test
    void candidateViewResolvesNestedTagsWithoutActiveBindings() {
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("test", "child");
        ResourceLocation root = ResourceLocation.fromNamespaceAndPath("test", "root");
        PreparedTag childTag = new PreparedTag("items", child, "tags/items/child.json", List.of("pack"), List.of("a"), false,
                List.of("minecraft:stone"), List.of(), Set.of(), Set.of());
        PreparedTag rootTag = new PreparedTag("items", root, "tags/items/root.json", List.of("pack"), List.of("b"), false,
                List.of("#test:child", "minecraft:dirt"), List.of(), Set.of(child), Set.of());
        PreparedTags tags = new PreparedTags(UUID.randomUUID(), Instant.now(), new ResourceSnapshot(Instant.now(), Map.of()),
                Map.of("items", new PreparedRegistryTags("items", Map.of(child, childTag, root, rootTag), 3)),
                new TagDependencyGraph(Map.of(root, Set.of(child), child, Set.of())),
                new TagDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false),
                ValidationReport.VALID, 2, 2, 3, Set.of("items"), Set.of());
        CandidateTagResolutionView view = new PreparedTagsResolutionView(tags);
        assertEquals(List.of(ResourceLocation.withDefaultNamespace("stone"), ResourceLocation.withDefaultNamespace("dirt")), view.resolvedMembers("items", root));
        assertTrue(view.contains("items", root, ResourceLocation.withDefaultNamespace("stone")));
        assertFalse(view.tagExists("items", ResourceLocation.fromNamespaceAndPath("test", "missing")));
        assertEquals(TagResolutionStatus.TAG_RESOLVED, view.resolve("items", root).status());
        assertEquals(TagResolutionStatus.TAG_MISSING, view.resolve("items", ResourceLocation.fromNamespaceAndPath("test", "missing")).status());
    }

    @Test
    void compositeRequiresTheSameSnapshotAndCopiesDependencyCollections() {
        ResourceSnapshot snapshot = new ResourceSnapshot(Instant.now(), Map.of());
        PreparedTags tags = new PreparedTags(UUID.randomUUID(), Instant.now(), snapshot, Map.of(), new TagDependencyGraph(Map.of()),
                new TagDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false), ValidationReport.VALID, 0, 0, 0, Set.of(), Set.of());
        PreparedRecipes recipes = new PreparedRecipes(UUID.randomUUID(), Instant.now(), snapshot, Map.of(), Map.of(), new RecipeDependencyGraph(Map.of()),
                new RecipeDelta(Set.of(), Set.of(), Set.of(), Set.of()), ValidationReport.VALID, 0, 0, 0, Set.of(), Set.of());
        PreparedTagsAndRecipes artifact = new PreparedTagsAndRecipes(UUID.randomUUID(), Instant.now(), snapshot, tags, recipes,
                new TagRecipeDependencyGraph(Map.of(), Map.of(), Set.of(), Set.of()),
                new TagRecipeDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()), ValidationReport.VALID);
        assertTrue(artifact.isApplicable());
        assertEquals(Set.of(ReloadCategory.TAGS, ReloadCategory.RECIPES), artifact.categories());
        ResourceSnapshot other = new ResourceSnapshot(Instant.now(), Map.of());
        PreparedTags mismatched = new PreparedTags(UUID.randomUUID(), Instant.now(), other, Map.of(), new TagDependencyGraph(Map.of()),
                new TagDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false), ValidationReport.VALID, 0, 0, 0, Set.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> new PreparedTagsAndRecipes(UUID.randomUUID(), Instant.now(), snapshot, mismatched, recipes,
                new TagRecipeDependencyGraph(Map.of(), Map.of(), Set.of(), Set.of()),
                new TagRecipeDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()), ValidationReport.VALID));
    }
}
