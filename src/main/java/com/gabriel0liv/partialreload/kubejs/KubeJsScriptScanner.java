package com.gabriel0liv.partialreload.kubejs;

import com.gabriel0liv.partialreload.resource.ResourceFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KubeJsScriptScanner {
    private static final Pattern IMPORT = Pattern.compile("(?:import|require)\\s*\\(?\\s*[\\\"']([^\\\"']+)");
    private static final Pattern EVENT = Pattern.compile("(?:ServerEvents|events)\\.([A-Za-z0-9_]+)");
    private static final Pattern ADDON = Pattern.compile("\\b(?:Java\\.loadClass|LootJS|MoreJS|create|botania|thermal|ars_nouveau)\\b", Pattern.CASE_INSENSITIVE);

    public KubeJsScriptSnapshot scan(Path kubeJsRoot, int maxFiles, long maxBytes) throws IOException {
        if (kubeJsRoot == null || Files.notExists(kubeJsRoot)) return new KubeJsScriptSnapshot(Instant.now(), Map.of());
        Map<String, KubeJsScriptDescriptor> result = new LinkedHashMap<>();
        long bytes = 0;
        try (var stream = Files.walk(kubeJsRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".js")).sorted().toList()) {
                if (result.size() >= maxFiles) throw new IOException("KUBEJS_LIMIT_EXCEEDED: script file limit");
                byte[] content = Files.readAllBytes(file);
                bytes += content.length;
                if (bytes > maxBytes) throw new IOException("KUBEJS_LIMIT_EXCEEDED: script byte limit");
                String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                String relative = kubeJsRoot.relativize(file).toString().replace('\\', '/');
                result.put(relative, describe(relative, content, text));
            }
        }
        return new KubeJsScriptSnapshot(Instant.now(), result);
    }

    private static KubeJsScriptDescriptor describe(String relative, byte[] content, String text) {
        KubeJsScriptType type = relative.startsWith("startup_scripts/") ? KubeJsScriptType.STARTUP
                : relative.startsWith("client_scripts/") ? KubeJsScriptType.CLIENT
                : relative.startsWith("server_scripts/") ? KubeJsScriptType.SERVER : KubeJsScriptType.MODULE;
        Set<String> events = new LinkedHashSet<>(); Matcher eventMatcher = EVENT.matcher(text);
        while (eventMatcher.find()) events.add(eventMatcher.group(1));
        Set<String> imports = new LinkedHashSet<>(); Matcher importMatcher = IMPORT.matcher(text);
        while (importMatcher.find()) imports.add(importMatcher.group(1));
        Set<String> addons = ADDON.matcher(text).results().map(m -> m.group()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean recipe = events.stream().anyMatch(e -> e.equalsIgnoreCase("recipes"));
        boolean other = events.stream().anyMatch(e -> !e.equalsIgnoreCase("recipes"));
        KubeJsScriptClassification classification = type == KubeJsScriptType.STARTUP ? KubeJsScriptClassification.STARTUP_SCRIPT
                : type == KubeJsScriptType.CLIENT ? KubeJsScriptClassification.CLIENT_SCRIPT
                : !addons.isEmpty() ? KubeJsScriptClassification.ADDON_DEPENDENT
                : recipe && !other ? KubeJsScriptClassification.RECIPE_EVENT_ONLY
                : recipe ? KubeJsScriptClassification.RECIPE_AND_OTHER_SERVER_EVENTS
                : events.isEmpty() ? KubeJsScriptClassification.DYNAMIC_OR_UNCLASSIFIABLE
                : KubeJsScriptClassification.NON_RECIPE_SERVER_SCRIPT;
        return new KubeJsScriptDescriptor(relative, type, ResourceFingerprint.sha256(content).hash(), content.length, classification, events, addons, imports);
    }
}
