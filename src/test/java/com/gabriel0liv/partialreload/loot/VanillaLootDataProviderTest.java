package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaLootDataProviderTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000010");

    @Test
    void everyPublicSelectionExpandsToTheCompleteInternalScope() {
        for (ReloadCategory requested : PreparedLootData.COMPLETE_SCOPE) {
            PreparedLootData artifact = emptyArtifact(Set.of(requested));
            assertEquals(Set.of(requested), artifact.requestedCategories());
            assertEquals(
                    Set.of(ReloadCategory.PREDICATES, ReloadCategory.ITEM_MODIFIERS, ReloadCategory.LOOT),
                    artifact.expandedCategories()
            );
        }
    }

    @Test
    void artifactCollectionsAndSnapshotAreImmutable() {
        PreparedLootData artifact = emptyArtifact(Set.of(ReloadCategory.LOOT));
        assertThrows(UnsupportedOperationException.class, () ->
                artifact.lootTables().put(ResourceLocation.parse("test:new"), null));
        assertThrows(UnsupportedOperationException.class, () ->
                artifact.requestedCategories().add(ReloadCategory.PREDICATES));
        assertThrows(UnsupportedOperationException.class, () ->
                artifact.sourceSnapshot().resources().put(ResourceLocation.parse("test:new"), null));
        assertEquals(ID, artifact.preparationId());
    }

    @Test
    void resourceLoaderUsesWinningPackAndCapturesWholeStack() throws Exception {
        InMemoryLootResourceManager manager = new InMemoryLootResourceManager()
                .put("lower", "test:loot_tables/table.json", "{\"pools\":[]}")
                .put("upper", "test:loot_tables/table.json", "{\"pools\":[{\"rolls\":0,\"entries\":[]}]}")
                .put("test", "test:predicates/predicate.json", "{}")
                .put("test", "test:item_modifiers/modifier.json", "[]");
        LootResourceView view = new LootResourceLoader().load(
                context(manager, Set.of(ReloadCategory.LOOT), null), System.nanoTime(), new ArrayList<>()
        );
        var table = view.sources().get(LootDataKind.LOOT_TABLE)
                .get(ResourceLocation.parse("test:table"));
        assertEquals("upper", table.descriptor().sourcePack());
        assertEquals(3, view.snapshot().resources().size());
        assertTrue(view.stackFingerprints().containsKey(
                ResourceLocation.parse("test:loot_tables/table.json")
        ));
    }

    @Test
    void deltaRecognizesRestorationFromLowerPack() throws Exception {
        InMemoryLootResourceManager manager = new InMemoryLootResourceManager()
                .put("lower", "test:loot_tables/table.json", "{\"pools\":[]}")
                .put("upper", "test:loot_tables/table.json", "{\"pools\":[{\"rolls\":0,\"entries\":[]}]}");
        LootResourceLoader loader = new LootResourceLoader();
        LootPreparationContext context = context(manager, Set.of(ReloadCategory.LOOT), null);
        ResourceSnapshot upper = loader.load(
                context, System.nanoTime(), new ArrayList<>()
        ).snapshot();
        manager.removeWinner("test:loot_tables/table.json");
        ResourceSnapshot lower = loader.load(
                context, System.nanoTime(), new ArrayList<>()
        ).snapshot();

        LootDataDelta delta = new LootDeltaCalculator().between(upper, lower);
        assertEquals(
                LootDataChangeKind.RESTORED_FROM_LOWER_PACK,
                delta.lootTables().changes().get(ResourceLocation.parse("test:table"))
        );
    }

    @Test
    void graphFindsReferencesMissingDependentsImpactAndCycles() {
        LootGraphExtractor extractor = new LootGraphExtractor();
        ResourceLocation first = ResourceLocation.parse("test:first");
        ResourceLocation second = ResourceLocation.parse("test:second");
        List<LootDependency> edges = new ArrayList<>();
        edges.addAll(extractor.extract(
                LootDataKind.LOOT_TABLE,
                first,
                JsonParser.parseString("{\"pools\":[{\"entries\":["
                        + "{\"type\":\"minecraft:loot_table\",\"name\":\"test:second\"},"
                        + "{\"type\":\"minecraft:loot_table\",\"name\":\"test:missing\"}]}]}")
        ));
        edges.addAll(extractor.extract(
                LootDataKind.LOOT_TABLE,
                second,
                JsonParser.parseString("{\"pools\":[{\"entries\":["
                        + "{\"type\":\"minecraft:loot_table\",\"name\":\"test:first\"}]}]}")
        ));
        LootDependencyGraph graph = new LootDependencyGraph(
                Set.of(
                        new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, first),
                        new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, second)
                ),
                edges
        );

        assertEquals(1, graph.missingReferences().size());
        assertFalse(graph.cycles().isEmpty());
        assertTrue(graph.dependentsOf(
                new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, second)
        ).contains(new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, first)));
        assertTrue(graph.impactedBy(
                new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, first)
        ).contains(new LootDependencyGraph.Node(LootDataKind.LOOT_TABLE, second)));
    }

    @Test
    void graphRecognizesAllRequiredStructuralRelationKinds() {
        List<LootDependency> edges = new LootGraphExtractor().extract(
                LootDataKind.LOOT_TABLE,
                ResourceLocation.parse("test:table"),
                JsonParser.parseString("""
                        {
                          "conditions":[{"condition":"minecraft:reference","name":"test:predicate"}],
                          "functions":[{"function":"minecraft:reference","name":"test:modifier"}],
                          "type":"minecraft:alternatives",
                          "children":[
                            {"type":"minecraft:loot_table","name":"test:nested"},
                            {"type":"minecraft:dynamic","name":"test:drop"}
                          ]
                        }
                        """)
        );
        Set<LootDependencyType> types = edges.stream()
                .map(LootDependency::type).collect(java.util.stream.Collectors.toSet());
        assertTrue(types.containsAll(Set.of(
                LootDependencyType.PREDICATE_REFERENCE,
                LootDependencyType.ITEM_MODIFIER_REFERENCE,
                LootDependencyType.LOOT_TABLE_REFERENCE,
                LootDependencyType.NESTED_CONDITION,
                LootDependencyType.NESTED_FUNCTION,
                LootDependencyType.COMPOSITE_ENTRY,
                LootDependencyType.DYNAMIC_DROP_REFERENCE
        )));
    }

    @Test
    void loaderDetectsToctouAndGlmWithoutParsingRuntimeRegistries() throws Exception {
        InMemoryLootResourceManager manager = new InMemoryLootResourceManager()
                .put("test", "test:loot_tables/table.json", "{\"pools\":[]}")
                .put("forge", "forge:loot_modifiers/global_loot_modifiers.json",
                        "{\"replace\":false,\"entries\":[]}");
        manager.mutateAfterCapture(() -> manager.replaceWinner(
                "test:loot_tables/table.json", "{\"pools\":[{\"rolls\":0,\"entries\":[]}]}"
        ));
        LootResourceLoader loader = new LootResourceLoader();
        LootPreparationContext context = context(manager, Set.of(ReloadCategory.LOOT), null);
        LootResourceView first = loader.load(context, System.nanoTime(), new ArrayList<>());
        LootResourceView second = loader.load(context, System.nanoTime(), new ArrayList<>());

        assertTrue(first.hasGlobalLootModifiers());
        assertFalse(first.stackFingerprints().equals(second.stackFingerprints()));
    }

    @Test
    void timeoutAndLimitsFailBeforeAnyCandidateCanBePublished() {
        InMemoryLootResourceManager manager = new InMemoryLootResourceManager()
                .put("test", "test:loot_tables/table.json", "{\"pools\":[]}");
        AtomicLong time = new AtomicLong();
        LootPreparationContext timeout = new LootPreparationContext(
                manager, Set.of(ReloadCategory.LOOT), null, Duration.ofNanos(1),
                100, 100, 100, 100_000, 100,
                CLOCK, () -> ID, time::incrementAndGet
        );
        LootPreparationException timeoutError = assertThrows(
                LootPreparationException.class,
                () -> new LootResourceLoader().load(timeout, 0, new ArrayList<>())
        );
        assertEquals("LOOT_PREPARATION_TIMEOUT", timeoutError.code());

        LootPreparationContext limit = new LootPreparationContext(
                manager, Set.of(ReloadCategory.LOOT), null, Duration.ofSeconds(60),
                100, 100, 100, 1, 100,
                CLOCK, () -> ID, System::nanoTime
        );
        LootPreparationException limitError = assertThrows(
                LootPreparationException.class,
                () -> new LootResourceLoader().load(limit, System.nanoTime(), new ArrayList<>())
        );
        assertEquals("LOOT_LIMIT_EXCEEDED", limitError.code());
    }

    @Test
    void contextRejectsEmptyScopeAndInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () ->
                new LootPreparationContext(
                        new InMemoryLootResourceManager(), Set.of(), null, Duration.ofSeconds(1),
                        1, 1, 1, 1, 1, CLOCK, () -> ID, System::nanoTime
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new LootPreparationContext(
                        new InMemoryLootResourceManager(), Set.of(ReloadCategory.LOOT), null,
                        Duration.ofSeconds(1), 0, 1, 1, 1, 1,
                        CLOCK, () -> ID, System::nanoTime
                ));
    }

    private static PreparedLootData emptyArtifact(Set<ReloadCategory> requested) {
        LootTypeDelta empty = new LootTypeDelta(Map.of());
        return new PreparedLootData(
                ID,
                Instant.now(CLOCK),
                new ResourceSnapshot(Instant.now(CLOCK), Map.of()),
                requested,
                Map.of(),
                Map.of(),
                Map.of(),
                new LootDependencyGraph(Set.of(), List.of()),
                new LootDataDelta(empty, empty, empty),
                ValidationReport.VALID
        );
    }

    private static LootPreparationContext context(
            InMemoryLootResourceManager manager,
            Set<ReloadCategory> requested,
            ResourceSnapshot active
    ) {
        return new LootPreparationContext(
                manager, requested, active, Duration.ofSeconds(60),
                100, 100, 100, 1_000_000, 10_000,
                CLOCK, () -> ID, System::nanoTime
        );
    }
}
