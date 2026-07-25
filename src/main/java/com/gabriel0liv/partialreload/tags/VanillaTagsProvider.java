package com.gabriel0liv.partialreload.tags;

import com.google.gson.*;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.resource.*;
import com.gabriel0liv.partialreload.validation.*;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Rebuilds tag candidates without calling bindTags or changing active holders. */
public final class VanillaTagsProvider {
    private static final Set<String> KNOWN = Set.of("items","blocks","fluids","entity_types","game_events","biomes","damage_type","functions","painting_variant","banner_pattern","instrument","point_of_interest_type","cat_variant","biome","configured_feature","placed_feature","structure","structure_set","template_pool","processor_list","flat_level_generator_preset","world_preset","worldgen/biome","worldgen/configured_feature","worldgen/placed_feature","worldgen/structure","worldgen/structure_set","worldgen/template_pool","worldgen/processor_list","worldgen/flat_level_generator_preset","worldgen/world_preset");

    public PreparedTags prepare(ResourceManager manager, RegistryAccess registries, ResourceSnapshot snapshot,
                                ResourceSnapshot baseline, int maxFiles, int maxTags, int maxEntries,
                                long maxBytes, long timeoutNanos, UUID id) {
        long deadline = System.nanoTime() + timeoutNanos; List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Map<ResourceLocation, PreparedTag>> byRegistry = new LinkedHashMap<>(); Map<ResourceLocation, Set<ResourceLocation>> deps = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> files = manager.listResources("tags", p -> p.getPath().endsWith(".json") && !p.getPath().startsWith("tags/functions/"));
        if (files.size() > maxFiles) issues.add(issue(ValidationSeverity.BLOCKER,"TAG_LIMIT_EXCEEDED",null,"tag file limit exceeded"));
        long bytes = 0; int totalEntries = 0;
        for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList()) {
            if (System.nanoTime() > deadline) { issues.add(issue(ValidationSeverity.BLOCKER,"TAG_PREPARATION_TIMEOUT",null,"tag preparation timed out")); break; }
            String registryPath = registryPath(entry.getKey().getPath());
            if (registryPath == null || registryPath.equals("functions")) { issues.add(issue(ValidationSeverity.INFO,"TAG_FUNCTION_DOMAIN_DELEGATED",entry.getKey(),"function tags are handled by the functions provider")); continue; }
            if (!KNOWN.contains(registryPath)) issues.add(issue(ValidationSeverity.BLOCKER,"TAG_REGISTRY_UNKNOWN",entry.getKey(),"unknown tag registry: " + registryPath));
            ResourceLocation tagId = tagId(entry.getKey(), registryPath); List<Resource> stack = manager.getResourceStack(entry.getKey());
            List<String> entries = new ArrayList<>(), removed = new ArrayList<>(), packs = new ArrayList<>(), hashes = new ArrayList<>(); Set<String> optional = new LinkedHashSet<>(); boolean replace = false;
            try {
                for (Resource resource : stack) {
                    packs.add(resource.sourcePackId());
                    try (var in = resource.open()) { byte[] raw = in.readAllBytes(); bytes += raw.length; if (bytes > maxBytes) { issues.add(issue(ValidationSeverity.BLOCKER,"TAG_LIMIT_EXCEEDED",tagId,"tag JSON byte limit exceeded")); break; } hashes.add(ResourceFingerprint.sha256(raw).hash());
                        JsonObject json = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (json.has("replace") && json.get("replace").getAsBoolean()) { entries.clear(); removed.clear(); replace = true; }
                        totalEntries += readValues(json.getAsJsonArray("values"), entries, optional, issues, tagId);
                        totalEntries += readValues(json.getAsJsonArray("remove"), removed, new LinkedHashSet<>(), issues, tagId);
                    }
                }
            } catch (Exception ex) { issues.add(issue(ValidationSeverity.ERROR,"TAG_JSON_SYNTAX_ERROR",tagId,ex.getMessage())); }
            if (totalEntries > maxEntries) issues.add(issue(ValidationSeverity.BLOCKER,"TAG_LIMIT_EXCEEDED",tagId,"tag entry limit exceeded"));
            LinkedHashSet<String> ordered = new LinkedHashSet<>(entries); ordered.removeAll(removed); Set<ResourceLocation> refs = new LinkedHashSet<>();
            for (String value : ordered) if (value.startsWith("#")) try { refs.add(ResourceLocation.parse(value.substring(1))); } catch (Exception ex) { issues.add(issue(ValidationSeverity.ERROR,"TAG_ENTRY_INVALID",tagId,value)); }
            PreparedTag prepared = new PreparedTag(registryPath, tagId, entry.getKey().getPath(), packs, hashes, replace, List.copyOf(ordered), List.copyOf(removed), refs, optional);
            byRegistry.computeIfAbsent(registryPath, k -> new LinkedHashMap<>()).put(tagId, prepared); deps.put(tagId, refs);
            validateElements(registries, registryPath, prepared, issues);
        }
        detectCycles(deps, issues);
        Map<String, PreparedRegistryTags> registriesOut = new LinkedHashMap<>(); int preparedCount=0, members=0;
        for (var e : byRegistry.entrySet()) { int count=e.getValue().values().stream().mapToInt(t->t.orderedEntries().size()).sum(); members+=count; preparedCount+=e.getValue().size(); registriesOut.put(e.getKey(),new PreparedRegistryTags(e.getKey(),e.getValue(),count)); }
        Set<ResourceLocation> current = new LinkedHashSet<>(); byRegistry.values().forEach(m->current.addAll(m.keySet()));
        Set<ResourceLocation> old = baseline == null ? Set.of() : baseline.resources().values().stream().filter(d -> d.category()==ReloadCategory.TAGS).map(ResourceDescriptor::logicalId).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> added=new HashSet<>(current); added.removeAll(old); Set<ResourceLocation> removedTags=new HashSet<>(old); removedTags.removeAll(current); Set<ResourceLocation> modified=new HashSet<>(current); modified.retainAll(old);
        return new PreparedTags(id, Instant.now(), snapshot, registriesOut, new TagDependencyGraph(deps), new TagDelta(added,modified,removedTags,Set.of(),Set.of(),Set.of(),false,false), new ValidationReport(issues), files.size(), preparedCount, members, byRegistry.keySet(), Set.of());
    }

    private static int readValues(JsonArray values, List<String> output, Set<String> optional, List<ValidationIssue> issues, ResourceLocation tag) {
        if (values == null) return 0; int count=0; for (JsonElement element : values) { String id; boolean required=true; try { if (element.isJsonPrimitive()) id=element.getAsString(); else { JsonObject o=element.getAsJsonObject(); id=o.get("id").getAsString(); required=!o.has("required") || o.get("required").getAsBoolean(); } if (id.isBlank()) throw new IllegalArgumentException("empty entry"); output.add(id); if (!required) optional.add(id); count++; } catch(Exception ex) { issues.add(issue(ValidationSeverity.ERROR,"TAG_ENTRY_INVALID",tag,ex.getMessage())); } } return count;
    }
    private static void validateElements(RegistryAccess access, String path, PreparedTag tag, List<ValidationIssue> issues) {
        ResourceKey<Registry<Object>> key = (ResourceKey) ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", canonical(path)));
        Optional<Registry<Object>> registry = access.registry(key); if (registry.isEmpty()) { issues.add(issue(ValidationSeverity.INFO,"TAG_REGISTRY_UNSUPPORTED",tag.id(),"registry not available in RegistryAccess: " + path)); return; }
        for (String value : tag.orderedEntries()) if (!value.startsWith("#") && !tag.missingOptionalEntries().contains(value)) { try { if (!registry.get().containsKey(ResourceLocation.parse(value))) issues.add(issue(ValidationSeverity.ERROR,"TAG_ELEMENT_REFERENCE_MISSING",tag.id(),value)); } catch(Exception ex) { issues.add(issue(ValidationSeverity.ERROR,"TAG_ENTRY_INVALID",tag.id(),value)); } }
    }
    private static void detectCycles(Map<ResourceLocation, Set<ResourceLocation>> graph, List<ValidationIssue> issues) { Set<ResourceLocation> visiting=new HashSet<>(), done=new HashSet<>(); for(ResourceLocation id:graph.keySet()) if(dfs(id,graph,visiting,done)) issues.add(issue(ValidationSeverity.ERROR,"TAG_REFERENCE_CYCLE",id,"nested tag cycle detected")); }
    private static boolean dfs(ResourceLocation id, Map<ResourceLocation,Set<ResourceLocation>> graph, Set<ResourceLocation> visiting, Set<ResourceLocation> done) { if(done.contains(id)) return false; if(!visiting.add(id)) return true; for(ResourceLocation next:graph.getOrDefault(id,Set.of())) if(dfs(next,graph,visiting,done)) return true; visiting.remove(id); done.add(id); return false; }
    private static String registryPath(String path) { if(!path.startsWith("tags/")) return null; String rest=path.substring(5); int slash=rest.indexOf('/'); if(slash<0)return null; String first=rest.substring(0,slash); if(first.equals("worldgen")){ int second=rest.indexOf('/',slash+1); return second<0?first:rest.substring(0,second); } return first; }
    private static ResourceLocation tagId(ResourceLocation file,String registry) { String prefix="tags/"+registry+"/"; String rest=file.getPath().substring(prefix.length()); if(rest.endsWith(".json")) rest=rest.substring(0,rest.length()-5); return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),rest); }
    private static String canonical(String path) { String p=path.startsWith("worldgen/")?path.substring(9):path; return switch(p){case "items"->"item";case "blocks"->"block";case "fluids"->"fluid";case "entity_types"->"entity_type";case "game_events"->"game_event";case "biomes"->"biome";case "damage_type"->"damage_type";default->p;}; }
    private static ValidationIssue issue(ValidationSeverity severity,String code,ResourceLocation resource,String message){ return new ValidationIssue(severity,code,ReloadCategory.TAGS,null,resource,null,message,null,null); }
}
