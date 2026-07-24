package com.gabriel0liv.partialreload.function;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FunctionDependencyGraph {
    private final Set<ResourceLocation> nodes;
    private final List<FunctionDependency> dependencies;
    private final Map<ResourceLocation, List<FunctionDependency>> outgoing;
    private final Map<ResourceLocation, List<FunctionDependency>> incoming;
    private final Set<Set<ResourceLocation>> cycles;

    public FunctionDependencyGraph(
            Set<ResourceLocation> nodes,
            List<FunctionDependency> dependencies,
            Map<ResourceLocation, Set<ResourceLocation>> resolvedTags
    ) {
        this.nodes = Set.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        Objects.requireNonNull(resolvedTags, "resolvedTags");
        this.outgoing = index(this.dependencies, true);
        this.incoming = index(this.dependencies, false);
        this.cycles = detectCycles(this.nodes, this.dependencies, resolvedTags);
    }

    public Set<ResourceLocation> nodes() {
        return nodes;
    }

    public List<FunctionDependency> dependencies() {
        return dependencies;
    }

    public List<FunctionDependency> dependenciesOf(ResourceLocation function) {
        return outgoing.getOrDefault(function, List.of());
    }

    public List<FunctionDependency> dependentsOf(ResourceLocation function) {
        return incoming.getOrDefault(function, List.of());
    }

    public Set<Set<ResourceLocation>> cycles() {
        return cycles;
    }

    private static Map<ResourceLocation, List<FunctionDependency>> index(
            List<FunctionDependency> edges,
            boolean bySource
    ) {
        Map<ResourceLocation, List<FunctionDependency>> mutable = new HashMap<>();
        edges.forEach(edge -> mutable.computeIfAbsent(
                bySource ? edge.source() : edge.target(),
                ignored -> new ArrayList<>()
        ).add(edge));
        Map<ResourceLocation, List<FunctionDependency>> result = new HashMap<>();
        mutable.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private static Set<Set<ResourceLocation>> detectCycles(
            Set<ResourceLocation> nodes,
            List<FunctionDependency> edges,
            Map<ResourceLocation, Set<ResourceLocation>> tags
    ) {
        Map<ResourceLocation, Set<ResourceLocation>> adjacency = new HashMap<>();
        nodes.forEach(node -> adjacency.put(node, new LinkedHashSet<>()));
        for (FunctionDependency edge : edges) {
            if (!nodes.contains(edge.source())) continue;
            switch (edge.type()) {
                case DIRECT_FUNCTION_CALL ->
                        adjacency.get(edge.source()).add(edge.target());
                case SCHEDULED_FUNCTION_CALL -> {
                    if (edge.tagTarget()) {
                        adjacency.get(edge.source()).addAll(tags.getOrDefault(edge.target(), Set.of()));
                    } else {
                        adjacency.get(edge.source()).add(edge.target());
                    }
                }
                case FUNCTION_TAG_CALL ->
                        adjacency.get(edge.source()).addAll(tags.getOrDefault(edge.target(), Set.of()));
                default -> {
                }
            }
        }

        Set<Set<ResourceLocation>> found = new LinkedHashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        ArrayDeque<ResourceLocation> stack = new ArrayDeque<>();
        Set<ResourceLocation> inStack = new HashSet<>();
        nodes.forEach(node -> visit(node, adjacency, visited, stack, inStack, found));

        Set<Set<ResourceLocation>> immutable = new LinkedHashSet<>();
        found.forEach(cycle -> immutable.add(Collections.unmodifiableSet(new LinkedHashSet<>(cycle))));
        return Collections.unmodifiableSet(immutable);
    }

    private static void visit(
            ResourceLocation node,
            Map<ResourceLocation, Set<ResourceLocation>> adjacency,
            Set<ResourceLocation> visited,
            ArrayDeque<ResourceLocation> stack,
            Set<ResourceLocation> inStack,
            Set<Set<ResourceLocation>> found
    ) {
        if (!visited.add(node)) return;
        stack.addLast(node);
        inStack.add(node);
        for (ResourceLocation target : adjacency.getOrDefault(node, Set.of())) {
            if (!visited.contains(target)) {
                visit(target, adjacency, visited, stack, inStack, found);
            } else if (inStack.contains(target)) {
                LinkedHashSet<ResourceLocation> cycle = new LinkedHashSet<>();
                boolean collect = false;
                for (ResourceLocation member : stack) {
                    if (member.equals(target)) collect = true;
                    if (collect) cycle.add(member);
                }
                if (!cycle.isEmpty()) found.add(cycle);
            }
        }
        stack.removeLast();
        inStack.remove(node);
    }
}
