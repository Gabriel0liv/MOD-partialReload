package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FunctionResourceLoader {
    private static final FileToIdConverter FUNCTIONS =
            new FileToIdConverter("functions", ".mcfunction");
    private static final FileToIdConverter TAGS =
            FileToIdConverter.json("tags/functions");

    FunctionResourceView load(FunctionPreparationContext context, long startedAt)
            throws FunctionPreparationException {
        Map<ResourceLocation, Resource> functionResources =
                FUNCTIONS.listMatchingResources(context.resourceManager());
        Map<ResourceLocation, List<Resource>> tagResources =
                TAGS.listMatchingResourceStacks(context.resourceManager());

        int resourceLayers = functionResources.size()
                + tagResources.values().stream().mapToInt(List::size).sum();
        if (resourceLayers > context.maxScannedResources()) {
            throw new FunctionPreparationException(
                    "PREPARATION_LIMIT",
                    "Function resource layers " + resourceLayers
                            + " exceed configured limit " + context.maxScannedResources()
            );
        }
        if (functionResources.size() > context.maxFunctionCount()) {
            throw new FunctionPreparationException(
                    "PREPARATION_LIMIT",
                    "Function count " + functionResources.size()
                            + " exceeds configured limit " + context.maxFunctionCount()
            );
        }

        Map<ResourceLocation, ResourceDescriptor> descriptors = new LinkedHashMap<>();
        Map<ResourceLocation, FunctionResourceView.FunctionSource> functions = new LinkedHashMap<>();
        int totalLines = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : sorted(functionResources).entrySet()) {
            checkDeadline(context, startedAt);
            ResourceLocation file = entry.getKey();
            ResourceLocation id = FUNCTIONS.fileToId(file);
            byte[] bytes = read(entry.getValue(), file);
            List<String> lines = lines(bytes);
            totalLines += lines.size();
            if (totalLines > context.maxFunctionLines()) {
                throw new FunctionPreparationException(
                        "PREPARATION_LIMIT",
                        "Function line count exceeds configured limit " + context.maxFunctionLines()
                );
            }
            functions.put(id, new FunctionResourceView.FunctionSource(
                    id,
                    file,
                    entry.getValue().sourcePackId(),
                    lines
            ));
            descriptors.put(file, new ResourceDescriptor(
                    file,
                    id,
                    ReloadCategory.FUNCTIONS,
                    entry.getValue().sourcePackId(),
                    ResourceFingerprint.sha256(bytes)
            ));
        }

        Map<ResourceLocation, FunctionResourceView.FunctionTagStack> tags = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : sorted(tagResources).entrySet()) {
            checkDeadline(context, startedAt);
            ResourceLocation file = entry.getKey();
            ResourceLocation id = TAGS.fileToId(file);
            List<FunctionResourceView.TagLayer> layers = new ArrayList<>();
            ByteArrayOutputStream framed = new ByteArrayOutputStream();
            List<String> packIds = new ArrayList<>();
            try (DataOutputStream output = new DataOutputStream(framed)) {
                for (Resource resource : entry.getValue()) {
                    checkDeadline(context, startedAt);
                    byte[] bytes = read(resource, file);
                    byte[] pack = resource.sourcePackId().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(pack.length);
                    output.write(pack);
                    output.writeInt(bytes.length);
                    output.write(bytes);
                    layers.add(new FunctionResourceView.TagLayer(resource.sourcePackId(), bytes));
                    packIds.add(resource.sourcePackId());
                }
            } catch (IOException exception) {
                throw new FunctionPreparationException(
                        "PREPARATION_IO",
                        "Could not fingerprint function tag stack " + file,
                        exception
                );
            }
            tags.put(id, new FunctionResourceView.FunctionTagStack(id, file, layers));
            descriptors.put(file, new ResourceDescriptor(
                    file,
                    id,
                    ReloadCategory.FUNCTIONS,
                    String.join(" -> ", packIds),
                    ResourceFingerprint.sha256(framed.toByteArray())
            ));
        }

        checkDeadline(context, startedAt);
        return new FunctionResourceView(
                new ResourceSnapshot(Instant.now(context.clock()), descriptors),
                functions,
                tags
        );
    }

    static void checkDeadline(FunctionPreparationContext context, long startedAt)
            throws FunctionPreparationException {
        if (context.nanoTime().getAsLong() - startedAt >= context.timeout().toNanos()) {
            throw new FunctionPreparationException(
                    "PREPARATION_TIMEOUT",
                    "Function preparation exceeded configured timeout"
            );
        }
    }

    private static byte[] read(Resource resource, ResourceLocation file)
            throws FunctionPreparationException {
        try (var input = resource.open()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new FunctionPreparationException(
                    "PREPARATION_IO",
                    "Could not read function resource " + file + " from " + resource.sourcePackId(),
                    exception
            );
        }
    }

    private static List<String> lines(byte[] bytes) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8
        ))) {
            return reader.lines().toList();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory UTF-8 read failed", impossible);
        }
    }

    private static <V> Map<ResourceLocation, V> sorted(Map<ResourceLocation, V> input) {
        LinkedHashMap<ResourceLocation, V> result = new LinkedHashMap<>();
        input.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }
}
