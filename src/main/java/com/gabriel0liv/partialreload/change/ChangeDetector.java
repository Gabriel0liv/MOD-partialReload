package com.gabriel0liv.partialreload.change;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChangeDetector {
    private ChangeDetector() {
    }

    public static ChangeSet diff(ResourceSnapshot activeReference, ResourceSnapshot latestScan) {
        Set<ResourceLocation> locations = new HashSet<>(activeReference.resources().keySet());
        locations.addAll(latestScan.resources().keySet());

        List<ResourceChange> changes = new ArrayList<>(locations.size());
        locations.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(location -> {
            ResourceDescriptor before = activeReference.resources().get(location);
            ResourceDescriptor after = latestScan.resources().get(location);
            ChangeKind kind;
            if (before == null) {
                kind = ChangeKind.ADDED;
            } else if (after == null) {
                kind = ChangeKind.REMOVED;
            } else if (before.equals(after)) {
                kind = ChangeKind.UNCHANGED;
            } else {
                kind = ChangeKind.MODIFIED;
            }
            changes.add(new ResourceChange(location, kind, before, after));
        });
        return new ChangeSet(changes);
    }
}
