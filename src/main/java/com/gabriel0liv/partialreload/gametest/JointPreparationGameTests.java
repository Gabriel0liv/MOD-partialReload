package com.gabriel0liv.partialreload.gametest;

import com.gabriel0liv.partialreload.PartialReloadMod;
import com.gabriel0liv.partialreload.tags.*;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(PartialReloadMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JointPreparationGameTests {
    private JointPreparationGameTests() { }

    @GameTest(template = "empty", batch = "phase4d-joint-prepare")
    public static void candidateTagViewIsReadOnly(GameTestHelper helper) {
        ResourceLocation tagId = ResourceLocation.fromNamespaceAndPath("partialreload", "candidate");
        PreparedTag tag = new PreparedTag("items", tagId, "tags/items/candidate.json", List.of("fixture"), List.of("hash"), true,
                List.of("minecraft:dirt"), List.of(), Set.of(), Set.of());
        PreparedTags prepared = new PreparedTags(UUID.randomUUID(), Instant.now(), new com.gabriel0liv.partialreload.resource.ResourceSnapshot(Instant.now(), Map.of()),
                Map.of("items", new PreparedRegistryTags("items", Map.of(tagId, tag), 1)),
                new TagDependencyGraph(Map.of()), new TagDelta(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false),
                ValidationReport.VALID, 1, 1, 1, Set.of("items"), Set.of());
        CandidateTagResolutionView view = new PreparedTagsResolutionView(prepared);
        helper.assertTrue(view.contains("items", tagId, ResourceLocation.withDefaultNamespace("dirt")), "candidate member missing");
        helper.assertTrue(view.resolvedMembers("items", tagId).size() == 1, "candidate view is not deterministic");
        helper.succeed();
    }
}
