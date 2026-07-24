package com.gabriel0liv.partialreload.function;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FunctionTagResolver {
    Result resolve(
            Map<ResourceLocation, FunctionResourceView.FunctionTagStack> stacks,
            Set<ResourceLocation> functions
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<ResourceLocation, List<Entry>> merged = parseAndMerge(stacks, issues);
        Map<ResourceLocation, Set<ResourceLocation>> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, VisitState> states = new HashMap<>();
        Set<ResourceLocation> reportedCycles = new HashSet<>();
        ArrayDeque<ResourceLocation> path = new ArrayDeque<>();

        merged.keySet().stream()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> resolveOne(
                        id,
                        merged,
                        functions,
                        resolved,
                        states,
                        path,
                        reportedCycles,
                        issues
                ));
        return new Result(resolved, issues);
    }

    private Map<ResourceLocation, List<Entry>> parseAndMerge(
            Map<ResourceLocation, FunctionResourceView.FunctionTagStack> stacks,
            List<ValidationIssue> issues
    ) {
        Map<ResourceLocation, List<Entry>> merged = new LinkedHashMap<>();
        stacks.values().stream()
                .sorted(java.util.Comparator.comparing(stack -> stack.id().toString()))
                .forEach(stack -> {
                    List<Entry> entries = merged.computeIfAbsent(stack.id(), ignored -> new ArrayList<>());
                    for (FunctionResourceView.TagLayer layer : stack.layers()) {
                        try {
                            JsonElement json = JsonParser.parseString(
                                    new String(layer.bytes(), StandardCharsets.UTF_8)
                            );
                            var result = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, json));
                            TagFile tagFile = result.result().orElse(null);
                            if (tagFile == null) {
                                String cause = result.error().map(error -> error.message()).orElse("Unknown codec error");
                                issues.add(issue(
                                        ValidationSeverity.ERROR,
                                        "FUNCTION_TAG_PARSE_ERROR",
                                        stack.id(),
                                        layer.packId(),
                                        "Could not parse function tag " + stack.id(),
                                        cause
                                ));
                                continue;
                            }
                            if (tagFile.replace()) entries.clear();
                            tagFile.entries().forEach(entry ->
                                    entries.add(new Entry(entry, false, layer.packId(), stack.file())));
                            tagFile.remove().forEach(entry ->
                                    entries.add(new Entry(entry, true, layer.packId(), stack.file())));
                        } catch (RuntimeException exception) {
                            issues.add(issue(
                                    ValidationSeverity.ERROR,
                                    "FUNCTION_TAG_PARSE_ERROR",
                                    stack.id(),
                                    layer.packId(),
                                    "Could not parse function tag " + stack.id(),
                                    exception.toString()
                            ));
                        }
                    }
                });
        return merged;
    }

    private Set<ResourceLocation> resolveOne(
            ResourceLocation id,
            Map<ResourceLocation, List<Entry>> merged,
            Set<ResourceLocation> functions,
            Map<ResourceLocation, Set<ResourceLocation>> resolved,
            Map<ResourceLocation, VisitState> states,
            ArrayDeque<ResourceLocation> path,
            Set<ResourceLocation> reportedCycles,
            List<ValidationIssue> issues
    ) {
        if (states.get(id) == VisitState.RESOLVED) return resolved.getOrDefault(id, Set.of());
        if (states.get(id) == VisitState.VISITING) {
            if (reportedCycles.add(id)) {
                issues.add(issue(
                        ValidationSeverity.ERROR,
                        "FUNCTION_TAG_CYCLE",
                        id,
                        null,
                        "Function tag cycle detected: " + path + " -> " + id,
                        null
                ));
            }
            return Set.of();
        }

        states.put(id, VisitState.VISITING);
        path.addLast(id);
        LinkedHashSet<ResourceLocation> values = new LinkedHashSet<>();
        for (Entry entry : merged.getOrDefault(id, List.of())) {
            TagEntry tagEntry = entry.value();
            if (tagEntry.isTag()) {
                if (!merged.containsKey(tagEntry.getId())) {
                    if (tagEntry.isRequired() && !entry.remove()) {
                        issues.add(issue(
                                ValidationSeverity.ERROR,
                                "FUNCTION_TAG_REFERENCE_MISSING",
                                id,
                                entry.packId(),
                                "Required function tag #" + tagEntry.getId() + " is missing",
                                null
                        ));
                    }
                    continue;
                }
                Set<ResourceLocation> referenced = resolveOne(
                        tagEntry.getId(),
                        merged,
                        functions,
                        resolved,
                        states,
                        path,
                        reportedCycles,
                        issues
                );
                if (entry.remove()) values.removeAll(referenced);
                else values.addAll(referenced);
            } else if (functions.contains(tagEntry.getId())) {
                if (entry.remove()) values.remove(tagEntry.getId());
                else values.add(tagEntry.getId());
            } else if (tagEntry.isRequired() && !entry.remove()) {
                issues.add(issue(
                        ValidationSeverity.ERROR,
                        "FUNCTION_REFERENCE_MISSING",
                        id,
                        entry.packId(),
                        "Required function " + tagEntry.getId() + " is missing from tag " + id,
                        null
                ));
            }
        }
        path.removeLast();
        states.put(id, VisitState.RESOLVED);
        Set<ResourceLocation> immutable =
                Collections.unmodifiableSet(new LinkedHashSet<>(values));
        resolved.put(id, immutable);
        return immutable;
    }

    private static ValidationIssue issue(
            ValidationSeverity severity,
            String code,
            ResourceLocation resource,
            String pack,
            String message,
            String cause
    ) {
        return new ValidationIssue(
                severity,
                code,
                ReloadCategory.FUNCTIONS,
                VanillaFunctionsProvider.ID,
                resource,
                pack,
                message,
                null,
                cause
        );
    }

    record Result(
            Map<ResourceLocation, Set<ResourceLocation>> tags,
            List<ValidationIssue> issues
    ) {
        Result {
            LinkedHashMap<ResourceLocation, Set<ResourceLocation>> copy = new LinkedHashMap<>();
            tags.forEach((id, values) ->
                    copy.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
            tags = Collections.unmodifiableMap(copy);
            issues = List.copyOf(issues);
        }
    }

    private record Entry(TagEntry value, boolean remove, String packId, ResourceLocation file) {
    }

    private enum VisitState {
        VISITING,
        RESOLVED
    }
}
