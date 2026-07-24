package com.gabriel0liv.partialreload.plan;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.change.ChangeKind;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.change.ResourceChange;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReloadPlannerTest {
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ReloadPlanner planner = new ReloadPlanner(
            Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC),
            () -> PLAN_ID
    );

    @Test
    void generatesImmutableReadOnlyPlan() {
        ReloadPlan plan = planner.createPlan(new ChangeSet(List.of(change(ReloadCategory.FUNCTIONS))));

        assertEquals(PLAN_ID, plan.id());
        assertEquals(ApplySupport.APPLY_NOT_IMPLEMENTED, plan.applySupport());
        assertEquals(SupportStatus.PREPARE_SUPPORTED, plan.supportStatus());
        assertTrue(plan.blockers().stream().anyMatch(value -> value.contains("APPLY_NOT_IMPLEMENTED")));
        assertThrows(UnsupportedOperationException.class, () -> plan.blockers().add("mutate"));
    }

    @Test
    void propagatesRestartRequiredBlocker() {
        ReloadPlan plan = planner.createPlan(new ChangeSet(List.of(change(ReloadCategory.DYNAMIC_REGISTRIES))));

        assertEquals(SupportStatus.RESTART_REQUIRED, plan.supportStatus());
        assertEquals(ReloadRisk.RESTART_REQUIRED, plan.risk());
        assertTrue(plan.blockers().stream().anyMatch(value -> value.contains("RESTART_REQUIRED")));
    }

    @Test
    void blocksUnknownAndPlannedProviders() {
        ReloadPlan unknown = planner.createPlan(new ChangeSet(List.of(change(ReloadCategory.UNKNOWN))));
        ReloadPlan origins = planner.createPlan(new ChangeSet(List.of(change(ReloadCategory.ORIGINS))));

        assertEquals(SupportStatus.UNKNOWN, unknown.supportStatus());
        assertTrue(unknown.blockers().stream().anyMatch(value -> value.contains("UNKNOWN_RESOURCE")));
        assertEquals(SupportStatus.PLANNED, origins.supportStatus());
        assertTrue(origins.blockers().stream().anyMatch(value -> value.contains("PROVIDER_PLANNED")));
    }

    @Test
    void plansPredicatesThroughTheJointLootGraph() {
        ReloadPlan predicates = planner.createPlan(
                new ChangeSet(List.of(change(ReloadCategory.PREDICATES)))
        );

        assertEquals(SupportStatus.PREPARE_SUPPORTED, predicates.supportStatus());
        assertTrue(predicates.warnings().stream()
                .anyMatch(value -> value.contains("LOOT_CATEGORY_SCOPE_EXPANDED")));
    }

    private static ResourceChange change(ReloadCategory category) {
        String path = category.commandName() + "/entry.json";
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("test", path);
        ResourceDescriptor descriptor = new ResourceDescriptor(
                location,
                ResourceLocation.fromNamespaceAndPath("test", "entry"),
                category,
                "test-pack",
                ResourceFingerprint.sha256(category.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        return new ResourceChange(location, ChangeKind.ADDED, null, descriptor);
    }
}
