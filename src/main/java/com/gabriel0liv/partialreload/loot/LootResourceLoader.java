package com.gabriel0liv.partialreload.loot;

import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LootResourceLoader {
    LootResourceView load(
            LootPreparationContext context,
            long startedAt,
            List<com.gabriel0liv.partialreload.validation.ValidationIssue> issues
    ) throws LootPreparationException {
        Map<ResourceLocation, ResourceDescriptor> descriptors = new LinkedHashMap<>();
        Map<ResourceLocation, String> stackFingerprints = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>> packStacks = new LinkedHashMap<>();
        Map<LootDataKind, Map<ResourceLocation, LootResourceView.Source>> sources =
                new EnumMap<>(LootDataKind.class);
        long totalBytes = 0;

        for (LootDataKind kind : LootDataKind.values()) {
            FileToIdConverter converter = FileToIdConverter.json(kind.directory());
            Map<ResourceLocation, List<Resource>> stacks =
                    converter.listMatchingResourceStacks(context.resourceManager());
            checkCount(kind, stacks.size(), context);
            Map<ResourceLocation, LootResourceView.Source> typed = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, List<Resource>> entry : sorted(stacks).entrySet()) {
                checkDeadline(context, startedAt);
                if (entry.getValue().isEmpty()) continue;
                ResourceLocation file = entry.getKey();
                ResourceLocation id = converter.fileToId(file);
                List<Layer> layers = new ArrayList<>();
                for (Resource resource : entry.getValue()) {
                    byte[] bytes = read(resource, file);
                    totalBytes += bytes.length;
                    if (totalBytes > context.maxTotalJsonBytes()) {
                        throw new LootPreparationException(
                                "LOOT_LIMIT_EXCEEDED",
                                "Loot JSON bytes exceed configured limit " + context.maxTotalJsonBytes()
                        );
                    }
                    layers.add(new Layer(resource, bytes));
                }
                Layer winner = layers.get(layers.size() - 1);
                ResourceDescriptor descriptor = new ResourceDescriptor(
                        file,
                        id,
                        kind.category(),
                        winner.resource().sourcePackId(),
                        ResourceFingerprint.sha256(winner.bytes())
                );
                JsonElement json = null;
                try {
                    json = JsonParser.parseReader(new InputStreamReader(
                            new java.io.ByteArrayInputStream(winner.bytes()),
                            StandardCharsets.UTF_8
                    ));
                } catch (JsonParseException exception) {
                    issues.add(VanillaLootDataProvider.issue(
                            com.gabriel0liv.partialreload.validation.ValidationSeverity.ERROR,
                            "LOOT_JSON_SYNTAX_ERROR",
                            kind,
                            id,
                            descriptor,
                            "$",
                            exception.getMessage(),
                            exception
                    ));
                }
                descriptors.put(file, descriptor);
                stackFingerprints.put(file, fingerprintStack(layers));
                packStacks.put(file, layers.stream()
                        .map(layer -> layer.resource().sourcePackId()).toList());
                if (json != null) {
                    typed.put(id, new LootResourceView.Source(
                            kind, id, file, winner.resource(), descriptor, json
                    ));
                }
            }
            sources.put(kind, Map.copyOf(typed));
        }

        boolean glm = !context.resourceManager()
                .listResourceStacks("loot_modifiers", id -> id.getPath().endsWith(".json"))
                .isEmpty();
        checkDeadline(context, startedAt);
        return new LootResourceView(
                new ResourceSnapshot(Instant.now(context.clock()), descriptors),
                stackFingerprints,
                packStacks,
                sources,
                glm
        );
    }

    static void checkDeadline(LootPreparationContext context, long startedAt)
            throws LootPreparationException {
        if (context.nanoTime().getAsLong() - startedAt >= context.timeout().toNanos()) {
            throw new LootPreparationException(
                    "LOOT_PREPARATION_TIMEOUT",
                    "Loot data preparation exceeded configured timeout"
            );
        }
    }

    private static void checkCount(LootDataKind kind, int count, LootPreparationContext context)
            throws LootPreparationException {
        int limit = switch (kind) {
            case PREDICATE -> context.maxPredicates();
            case ITEM_MODIFIER -> context.maxItemModifiers();
            case LOOT_TABLE -> context.maxLootTables();
        };
        if (count > limit) {
            throw new LootPreparationException(
                    "LOOT_LIMIT_EXCEEDED",
                    kind + " count " + count + " exceeds configured limit " + limit
            );
        }
    }

    private static byte[] read(Resource resource, ResourceLocation file)
            throws LootPreparationException {
        try (var input = resource.open()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new LootPreparationException(
                    "LOOT_PREPARATION_IO",
                    "Could not read " + file + " from " + resource.sourcePackId(),
                    exception
            );
        }
    }

    private static String fingerprintStack(List<Layer> layers) throws LootPreparationException {
        try {
            ByteArrayOutputStream framed = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(framed)) {
                for (Layer layer : layers) {
                    byte[] pack = layer.resource().sourcePackId().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(pack.length);
                    output.write(pack);
                    output.writeInt(layer.bytes().length);
                    output.write(layer.bytes());
                }
            }
            return ResourceFingerprint.sha256(framed.toByteArray()).hash();
        } catch (IOException exception) {
            throw new LootPreparationException(
                    "LOOT_PREPARATION_IO", "Could not fingerprint loot resource stack", exception
            );
        }
    }

    private static <V> Map<ResourceLocation, V> sorted(Map<ResourceLocation, V> input) {
        LinkedHashMap<ResourceLocation, V> result = new LinkedHashMap<>();
        input.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private record Layer(Resource resource, byte[] bytes) {
    }
}
