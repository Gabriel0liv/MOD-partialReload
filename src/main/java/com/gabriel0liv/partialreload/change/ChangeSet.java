package com.gabriel0liv.partialreload.change;

import com.gabriel0liv.partialreload.api.ReloadCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record ChangeSet(List<ResourceChange> changes) {
    public ChangeSet {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public List<ResourceChange> changedResources() {
        return changes.stream().filter(change -> change.kind() != ChangeKind.UNCHANGED).toList();
    }

    public ChangeSet onlyChanged() {
        return new ChangeSet(changedResources());
    }

    public ChangeSet forCategory(ReloadCategory category) {
        return new ChangeSet(changes.stream().filter(change -> change.category() == category).toList());
    }

    public Map<ReloadCategory, List<ResourceChange>> groupByCategory() {
        return changes.stream().collect(Collectors.groupingBy(
                ResourceChange::category,
                () -> new EnumMap<>(ReloadCategory.class),
                Collectors.toUnmodifiableList()
        ));
    }
}
