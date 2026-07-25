package com.gabriel0liv.partialreload.kubejs;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KubeJsScriptSnapshot(Instant capturedAt, Map<String, KubeJsScriptDescriptor> scripts) {
    public KubeJsScriptSnapshot {
        scripts = Map.copyOf(scripts);
    }

    public List<KubeJsScriptDescriptor> recipeCandidates() {
        return scripts.values().stream().filter(s -> s.classification() == KubeJsScriptClassification.RECIPE_EVENT_ONLY).toList();
    }
}
