package com.gabriel0liv.partialreload.loot;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LootDependencyGraph {
    public record Node(LootDataKind kind, ResourceLocation id) {
        public Node {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }

    private final Set<Node> nodes;
    private final List<LootDependency> dependencies;
    private final Set<LootDependency> missing;

    public LootDependencyGraph(Set<Node> nodes, List<LootDependency> dependencies) {
        this.nodes = Set.copyOf(nodes);
        this.dependencies = List.copyOf(dependencies);
        this.missing = this.dependencies.stream()
                .filter(edge -> isReference(edge.type()))
                .filter(edge -> !this.nodes.contains(new Node(edge.targetKind(), edge.target())))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<Node> nodes() {
        return nodes;
    }

    public List<LootDependency> dependencies() {
        return dependencies;
    }

    public Set<LootDependency> missingReferences() {
        return missing;
    }

    public Set<Node> dependenciesOf(Node node) {
        return dependencies.stream()
                .filter(edge -> edge.sourceKind() == node.kind() && edge.source().equals(node.id()))
                .map(edge -> new Node(edge.targetKind(), edge.target()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<Node> dependentsOf(Node node) {
        return dependencies.stream()
                .filter(edge -> edge.targetKind() == node.kind() && edge.target().equals(node.id()))
                .map(edge -> new Node(edge.sourceKind(), edge.source()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<Node> impactedBy(Node changed) {
        Set<Node> result = new LinkedHashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(changed);
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Node dependent : dependentsOf(current)) {
                if (result.add(dependent)) queue.add(dependent);
            }
        }
        return Set.copyOf(result);
    }

    public List<Set<Node>> cycles() {
        Map<Node, Set<Node>> adjacency = new HashMap<>();
        nodes.forEach(node -> adjacency.put(node, new HashSet<>()));
        dependencies.stream().filter(edge -> isReference(edge.type())).forEach(edge -> {
            Node source = new Node(edge.sourceKind(), edge.source());
            Node target = new Node(edge.targetKind(), edge.target());
            if (nodes.contains(source) && nodes.contains(target)) adjacency.get(source).add(target);
        });
        List<Set<Node>> cycles = new ArrayList<>();
        for (Node start : nodes) {
            findCycles(start, start, adjacency, new LinkedHashSet<>(), cycles);
        }
        return cycles.stream().distinct().toList();
    }

    private static void findCycles(
            Node start,
            Node current,
            Map<Node, Set<Node>> adjacency,
            LinkedHashSet<Node> path,
            List<Set<Node>> result
    ) {
        if (!path.add(current)) return;
        for (Node next : adjacency.getOrDefault(current, Set.of())) {
            if (next.equals(start)) result.add(Set.copyOf(path));
            else if (!path.contains(next)) findCycles(start, next, adjacency, path, result);
        }
        path.remove(current);
    }

    private static boolean isReference(LootDependencyType type) {
        return type == LootDependencyType.LOOT_TABLE_REFERENCE
                || type == LootDependencyType.PREDICATE_REFERENCE
                || type == LootDependencyType.ITEM_MODIFIER_REFERENCE;
    }
}

