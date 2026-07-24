package com.gabriel0liv.partialreload.change;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeDetectorTest {
    @Test
    void detectsAddedModifiedRemovedAndUnchangedResources() {
        ResourceDescriptor removed = descriptor("removed", ReloadCategory.FUNCTIONS, "old");
        ResourceDescriptor modifiedOld = descriptor("modified", ReloadCategory.RECIPES, "old");
        ResourceDescriptor unchanged = descriptor("unchanged", ReloadCategory.TAGS, "same");
        ResourceDescriptor added = descriptor("added", ReloadCategory.LOOT, "new");
        ResourceDescriptor modifiedNew = descriptor("modified", ReloadCategory.RECIPES, "new");

        ResourceSnapshot before = snapshot(removed, modifiedOld, unchanged);
        ResourceSnapshot after = snapshot(added, modifiedNew, unchanged);
        ChangeSet result = ChangeDetector.diff(before, after);

        assertEquals(ChangeKind.ADDED, kind(result, "added"));
        assertEquals(ChangeKind.MODIFIED, kind(result, "modified"));
        assertEquals(ChangeKind.REMOVED, kind(result, "removed"));
        assertEquals(ChangeKind.UNCHANGED, kind(result, "unchanged"));
        assertEquals(1, result.groupByCategory().get(ReloadCategory.LOOT).size());
        assertEquals(3, result.changedResources().size());
    }

    private static ChangeKind kind(ChangeSet set, String path) {
        return set.changes().stream()
                .filter(change -> change.location().getPath().equals(path + ".json"))
                .findFirst()
                .orElseThrow()
                .kind();
    }

    private static ResourceSnapshot snapshot(ResourceDescriptor... descriptors) {
        return new ResourceSnapshot(
                Instant.EPOCH,
                java.util.Arrays.stream(descriptors)
                        .collect(java.util.stream.Collectors.toMap(ResourceDescriptor::location, value -> value))
        );
    }

    private static ResourceDescriptor descriptor(String path, ReloadCategory category, String content) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("test", path + ".json");
        return new ResourceDescriptor(
                location,
                ResourceLocation.fromNamespaceAndPath("test", path),
                category,
                "test-pack",
                ResourceFingerprint.sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }
}
