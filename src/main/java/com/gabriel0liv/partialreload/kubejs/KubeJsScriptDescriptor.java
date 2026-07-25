package com.gabriel0liv.partialreload.kubejs;

import java.util.Set;

public record KubeJsScriptDescriptor(String relativePath, KubeJsScriptType type,
                                     String contentHash, long size,
                                     KubeJsScriptClassification classification,
                                     Set<String> events, Set<String> addons,
                                     Set<String> imports) {
    public KubeJsScriptDescriptor {
        events = Set.copyOf(events);
        addons = Set.copyOf(addons);
        imports = Set.copyOf(imports);
    }
}
