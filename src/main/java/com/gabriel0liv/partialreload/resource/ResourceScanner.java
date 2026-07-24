package com.gabriel0liv.partialreload.resource;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ScanContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResourceScanner {
    private static final List<String> SCAN_ROOTS = List.of(
            "functions",
            "advancements",
            "predicates",
            "recipes",
            "loot_tables",
            "item_modifiers",
            "tags",
            "powers",
            "origins",
            "origin_layers",
            "global_power_sets",
            "global_powers",
            "worldgen",
            "damage_type",
            "silentgear_materials",
            "silentgear_traits"
    );

    private final Clock clock;

    public ResourceScanner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ResourceSnapshot scan(ScanContext context) throws ResourceScanException {
        long deadline = System.nanoTime() + context.timeout().toNanos();
        Map<ResourceLocation, Resource> visibleResources = new LinkedHashMap<>();
        try {
            for (String root : SCAN_ROOTS) {
                ensureBeforeDeadline(deadline);
                visibleResources.putAll(context.resourceManager().listResources(root, location -> true));
            }
        } catch (ResourceScanException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResourceScanException("SCAN_IO", "Could not enumerate server data resources", exception);
        }

        if (visibleResources.size() > context.maxResources()) {
            throw new ResourceScanException(
                    "SCAN_LIMIT",
                    "Resource count " + visibleResources.size() + " exceeds configured limit " + context.maxResources()
            );
        }

        Map<ResourceLocation, ResourceDescriptor> descriptors = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : visibleResources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .toList()) {
            ensureBeforeDeadline(deadline);
            ResourceLocation location = entry.getKey();
            try (InputStream input = entry.getValue().open()) {
                ResourceFingerprint fingerprint = ResourceFingerprint.sha256(input);
                ReloadCategory category = classifyPath(location.getPath());
                descriptors.put(location, new ResourceDescriptor(
                        location,
                        logicalId(location, category),
                        category,
                        entry.getValue().sourcePackId(),
                        fingerprint
                ));
            } catch (IOException exception) {
                throw new ResourceScanException(
                        "SCAN_IO",
                        "Could not read server data resource " + location,
                        exception
                );
            }
        }

        ensureBeforeDeadline(deadline);
        return new ResourceSnapshot(Instant.now(clock), descriptors);
    }

    public static ReloadCategory classifyPath(String rawPath) {
        String path = rawPath.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (matchesJsonDirectory(path, "advancements")) return ReloadCategory.ADVANCEMENTS;
        if (matchesJsonDirectory(path, "predicates")) return ReloadCategory.PREDICATES;
        if (matchesJsonDirectory(path, "recipes")) return ReloadCategory.RECIPES;
        if (matchesJsonDirectory(path, "loot_tables")) return ReloadCategory.LOOT;
        if (matchesJsonDirectory(path, "item_modifiers")) return ReloadCategory.ITEM_MODIFIERS;
        if (matchesJsonDirectory(path, "tags/functions")) return ReloadCategory.FUNCTIONS;
        if (matchesJsonDirectory(path, "tags")) return ReloadCategory.TAGS;
        if (matchesJsonDirectory(path, "powers")
                || matchesJsonDirectory(path, "origins")
                || matchesJsonDirectory(path, "origin_layers")
                || matchesJsonDirectory(path, "global_power_sets")
                || matchesJsonDirectory(path, "global_powers")) {
            return ReloadCategory.ORIGINS;
        }
        if (path.startsWith("worldgen/") || matchesJsonDirectory(path, "damage_type")) {
            return ReloadCategory.DYNAMIC_REGISTRIES;
        }
        if (matchesJsonDirectory(path, "silentgear_materials")
                || matchesJsonDirectory(path, "silentgear_traits")) {
            return ReloadCategory.SILENTGEAR;
        }
        if (path.startsWith("functions/") && path.endsWith(".mcfunction")) {
            return ReloadCategory.FUNCTIONS;
        }
        return ReloadCategory.UNKNOWN;
    }

    static List<String> scanRoots() {
        return SCAN_ROOTS;
    }

    public static ResourceLocation logicalId(ResourceLocation location, ReloadCategory category) {
        String path = location.getPath();
        String prefix = switch (category) {
            case FUNCTIONS -> "functions/";
            case ADVANCEMENTS -> "advancements/";
            case PREDICATES -> "predicates/";
            case RECIPES -> "recipes/";
            case LOOT -> "loot_tables/";
            case ITEM_MODIFIERS -> "item_modifiers/";
            case TAGS -> "tags/";
            case ORIGINS, SILENTGEAR, DYNAMIC_REGISTRIES, KUBEJS, UNKNOWN -> "";
        };
        if (!prefix.isEmpty() && path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        } else if (path.endsWith(".mcfunction")) {
            path = path.substring(0, path.length() - ".mcfunction".length());
        }
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path);
    }

    private static boolean matchesJsonDirectory(String path, String directory) {
        return path.startsWith(directory + "/") && path.endsWith(".json");
    }

    private static void ensureBeforeDeadline(long deadline) throws ResourceScanException {
        if (System.nanoTime() - deadline >= 0) {
            throw new ResourceScanException("SCAN_TIMEOUT", "Resource scan exceeded configured timeout");
        }
    }
}
