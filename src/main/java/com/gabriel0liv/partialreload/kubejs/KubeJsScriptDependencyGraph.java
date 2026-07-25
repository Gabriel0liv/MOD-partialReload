package com.gabriel0liv.partialreload.kubejs;

import java.util.Map;
import java.util.Set;

public record KubeJsScriptDependencyGraph(Map<String, Set<String>> imports) {
    public KubeJsScriptDependencyGraph {
        imports = imports.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }
}
