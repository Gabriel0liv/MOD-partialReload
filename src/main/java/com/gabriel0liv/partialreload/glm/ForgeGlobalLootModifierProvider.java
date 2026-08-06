package com.gabriel0liv.partialreload.glm;

import com.gabriel0liv.partialreload.api.*;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ProviderPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceDescriptor;
import com.gabriel0liv.partialreload.resource.ResourceFingerprint;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class ForgeGlobalLootModifierProvider implements ReloadProvider {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("partialreload", "forge_glm");
    public static final ResourceLocation GLOBAL_LIST = ResourceLocation.fromNamespaceAndPath(
            "forge", "loot_modifiers/global_loot_modifiers.json");
    private final ResourceScanner scanner;

    public ForgeGlobalLootModifierProvider(ResourceScanner scanner) {
        this.scanner = Objects.requireNonNull(scanner);
    }

    @Override public ResourceLocation id() { return ID; }
    @Override public Set<ReloadCategory> categories() { return Set.of(ReloadCategory.GLOBAL_LOOT_MODIFIERS); }
    @Override public ProviderCompatibility compatibility(ReloadEnvironment environment) {
        return ProviderCompatibility.COMMIT_SUPPORTED;
    }
    @Override public ScanResult scan(ScanContext context) throws PartialReloadException {
        return new ScanResult(scanner.scan(context));
    }
    @Override public ValidationReport validate(ValidationContext context, ChangeSet changeSet) {
        return ValidationReport.VALID;
    }
    @Override public ProviderPlan createPlan(PlanningContext context, ChangeSet changeSet) {
        ChangeSet selected = new ChangeSet(changeSet.changedResources().stream()
                .filter(change -> change.category() == ReloadCategory.GLOBAL_LOOT_MODIFIERS).toList());
        var plan = new ReloadPlanner(context.clock(), UUID::randomUUID).createPlan(selected);
        return new ProviderPlan(ID, Set.of(ReloadCategory.GLOBAL_LOOT_MODIFIERS),
                selected.changedResources(), plan.risk(),
                Set.of("ordered Forge LootModifierManager generation"),
                List.of(), List.of(), plan.supportStatus());
    }

    public PreparedGlobalLootModifiers prepare(GlobalLootModifierPreparationContext context) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<ResourceLocation, String> listSources = Map.of();
        List<ResourceLocation> ordered = List.of();
        try {
            List<GlobalLootModifierStack.Layer> layers = new ArrayList<>();
            for (Resource resource : context.resourceManager().getResourceStack(GLOBAL_LIST)) {
                JsonObject object = readObject(resource);
                if (object.has("replace") && (!object.get("replace").isJsonPrimitive()
                        || !object.get("replace").getAsJsonPrimitive().isBoolean())) {
                    throw new IllegalArgumentException("replace must be boolean");
                }
                boolean replace = object.has("replace") && object.get("replace").getAsBoolean();
                JsonElement entriesValue = object.get("entries");
                if (!(entriesValue instanceof JsonArray entries)) {
                    throw new IllegalArgumentException("entries must be an array");
                }
                List<ResourceLocation> layerEntries = new ArrayList<>();
                for (JsonElement entry : entries) {
                    if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("entry must be a resource location string");
                    }
                    ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
                    if (id == null) throw new IllegalArgumentException("invalid entry " + entry);
                    layerEntries.add(id);
                }
                layers.add(new GlobalLootModifierStack.Layer(resource.sourcePackId(), replace, layerEntries));
            }
            GlobalLootModifierStack.Result merged = GlobalLootModifierStack.merge(layers);
            ordered = merged.orderedIds();
            listSources = merged.sourcePacks();
        } catch (Exception error) {
            issues.add(issue("GLM_GLOBAL_LIST_INVALID", GLOBAL_LIST, error.getMessage()));
        }

        LinkedHashMap<ResourceLocation, IGlobalLootModifier> decoded = new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, GlobalLootModifierEntryEvidence> evidence = new LinkedHashMap<>();
        for (int position = 0; position < ordered.size(); position++) {
            ResourceLocation id = ordered.get(position);
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "loot_modifiers/" + id.getPath() + ".json");
            try {
                List<Resource> stack = context.resourceManager().getResourceStack(file);
                if (stack.isEmpty()) throw new IllegalStateException("GLM_ENTRY_FILE_MISSING");
                Resource winner = stack.get(stack.size() - 1);
                JsonObject json = readObject(winner);
                if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
                    throw new IllegalStateException("GLM_TYPE_MISSING");
                }
                ResourceLocation type = ResourceLocation.tryParse(json.get("type").getAsString());
                if (type == null || !ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get().containsKey(type)) {
                    throw new IllegalStateException("GLM_TYPE_UNKNOWN: " + json.get("type"));
                }
                StringBuilder codecError = new StringBuilder();
                Optional<IGlobalLootModifier> result = IGlobalLootModifier.DIRECT_CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(message -> codecError.append(message));
                if (codecError.length() > 0 || result.isEmpty()) {
                    throw new IllegalStateException(classifyCodecError(codecError.toString()) + ": " + codecError);
                }
                decoded.put(id, result.orElseThrow());
                List<String> packs = stack.stream().map(Resource::sourcePackId).toList();
                String hash;
                try (var input = winner.open()) { hash = ResourceFingerprint.sha256(input).hash(); }
                evidence.put(id, new GlobalLootModifierEntryEvidence(id, listSources.get(id), position,
                        winner.sourcePackId(), hash, packs));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                String code = message.startsWith("GLM_") ? message.split(":", 2)[0] : "GLM_DECODE_FAILED";
                issues.add(issue(code, id, message));
            }
        }
        return new PreparedGlobalLootModifiers(context.idSupplier().get(), Instant.now(context.clock()),
                context.sourceSnapshot(), ordered, decoded, evidence,
                delta(context.activeReference(), context.sourceSnapshot(), context.activeOrderedIds(), ordered, evidence),
                new ValidationReport(issues));
    }

    private static GlobalLootModifierDelta delta(com.gabriel0liv.partialreload.resource.ResourceSnapshot previous,
                                                 com.gabriel0liv.partialreload.resource.ResourceSnapshot current,
                                                 List<ResourceLocation> previousOrder,
                                                 List<ResourceLocation> ordered,
                                                 Map<ResourceLocation, GlobalLootModifierEntryEvidence> evidence) {
        Map<ResourceLocation, ResourceDescriptor> before = glmByLogicalId(previous);
        Map<ResourceLocation, ResourceDescriptor> after = glmByLogicalId(current);
        List<ResourceLocation> added = new ArrayList<>(), removed = new ArrayList<>(), modified = new ArrayList<>(),
                moved = new ArrayList<>(), restored = new ArrayList<>(), unchanged = new ArrayList<>();
        Set<ResourceLocation> contentUnchanged = new LinkedHashSet<>();
        for (ResourceLocation id : ordered) {
            ResourceDescriptor old = before.get(id), now = after.get(id);
            if (old == null) added.add(id);
            else if (now == null) removed.add(id);
            else if (!old.fingerprint().hash().equals(now.fingerprint().hash())) {
                if (!old.sourcePack().equals(now.sourcePack())) restored.add(id); else modified.add(id);
            } else contentUnchanged.add(id);
        }
        moved.addAll(GlobalLootModifierDelta.movedIds(previousOrder, ordered, contentUnchanged));
        contentUnchanged.stream().filter(id -> !moved.contains(id)).forEach(unchanged::add);
        before.keySet().stream().filter(id -> !after.containsKey(id)).forEach(removed::add);
        return new GlobalLootModifierDelta(added, removed, modified, moved, restored, unchanged);
    }

    private static Map<ResourceLocation, ResourceDescriptor> glmByLogicalId(
            com.gabriel0liv.partialreload.resource.ResourceSnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<ResourceLocation, ResourceDescriptor> result = new LinkedHashMap<>();
        snapshot.resources().values().stream()
                .filter(value -> value.category() == ReloadCategory.GLOBAL_LOOT_MODIFIERS)
                .filter(value -> !value.location().equals(GLOBAL_LIST))
                .forEach(value -> result.put(value.logicalId(), value));
        return result;
    }

    private static JsonObject readObject(Resource resource) throws Exception {
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root must be object");
            return parsed.getAsJsonObject();
        }
    }

    private static String classifyCodecError(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("registry") || lower.contains("unknown element")) return "GLM_REGISTRY_REFERENCE_MISSING";
        if (lower.contains("condition")) return "GLM_CONDITION_INVALID";
        return "GLM_CODEC_ERROR";
    }

    private static ValidationIssue issue(String code, ResourceLocation resource, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, code,
                ReloadCategory.GLOBAL_LOOT_MODIFIERS, ID, resource, null,
                message == null ? code : message, null, message);
    }
}
