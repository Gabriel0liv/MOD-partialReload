package com.gabriel0liv.partialreload.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PartialReloadConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue COMMAND_PERMISSION_LEVEL;
    private static final ForgeConfigSpec.BooleanValue LOG_RESOURCE_DETAILS;
    private static final ForgeConfigSpec.IntValue MAX_SCANNED_RESOURCES;
    private static final ForgeConfigSpec.IntValue SCAN_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.BooleanValue ENABLE_UNKNOWN_RESOURCE_REPORTING;
    private static final ForgeConfigSpec.IntValue PREPARE_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue MAX_FUNCTION_COUNT;
    private static final ForgeConfigSpec.IntValue MAX_FUNCTION_LINES;
    private static final ForgeConfigSpec.IntValue LOOT_PREPARE_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue MAX_PREDICATES;
    private static final ForgeConfigSpec.IntValue MAX_ITEM_MODIFIERS;
    private static final ForgeConfigSpec.IntValue MAX_LOOT_TABLES;
    private static final ForgeConfigSpec.LongValue MAX_TOTAL_JSON_BYTES;
    private static final ForgeConfigSpec.IntValue MAX_DEPENDENCY_EDGES;
    private static final ForgeConfigSpec.IntValue MAX_RECIPES;
    private static final ForgeConfigSpec.LongValue MAX_RECIPE_JSON_BYTES;
    private static final ForgeConfigSpec.IntValue KUBEJS_PREPARE_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue KUBEJS_MAX_SCRIPT_FILES;
    private static final ForgeConfigSpec.LongValue KUBEJS_MAX_SCRIPT_BYTES;
    private static final ForgeConfigSpec.IntValue KUBEJS_MAX_MUTATIONS;
    private static final ForgeConfigSpec.IntValue KUBEJS_MAX_FINAL_RECIPES;
    private static final ForgeConfigSpec.IntValue KUBEJS_MAX_DEPENDENCY_EDGES;
    private static final ForgeConfigSpec.IntValue TAG_PREPARE_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue MAX_TAG_FILES;
    private static final ForgeConfigSpec.IntValue MAX_TAGS;
    private static final ForgeConfigSpec.IntValue MAX_TAG_ENTRIES;
    private static final ForgeConfigSpec.IntValue MAX_TAG_DEPENDENCY_EDGES;
    private static final ForgeConfigSpec.LongValue MAX_TAG_JSON_BYTES;
    private static final ForgeConfigSpec.IntValue CLIENT_SYNC_HANDSHAKE_TIMEOUT_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        COMMAND_PERMISSION_LEVEL = builder
                .comment("Operator permission level required for Partial Reload commands.")
                .defineInRange("command_permission_level", 4, 0, 4);
        LOG_RESOURCE_DETAILS = builder
                .comment("Include individual changed resources in operator command output.")
                .define("log_resource_details", false);
        MAX_SCANNED_RESOURCES = builder
                .comment("Hard limit for resources in one scan.")
                .defineInRange("max_scanned_resources", 100_000, 1, 1_000_000);
        SCAN_TIMEOUT_SECONDS = builder
                .comment("Cooperative timeout for one scan.")
                .defineInRange("scan_timeout_seconds", 60, 1, 3_600);
        builder.pop();

        builder.push("preparation");
        PREPARE_TIMEOUT_SECONDS = builder
                .comment("Cooperative timeout for function preparation.")
                .defineInRange("prepare_timeout_seconds", 60, 1, 3_600);
        MAX_FUNCTION_COUNT = builder
                .comment("Hard limit for functions in one prepared generation.")
                .defineInRange("max_function_count", 100_000, 1, 1_000_000);
        MAX_FUNCTION_LINES = builder
                .comment("Hard limit for total function source lines in one preparation.")
                .defineInRange("max_function_lines", 1_000_000, 1, 10_000_000);
        builder.push("loot");
        LOOT_PREPARE_TIMEOUT_SECONDS = builder
                .comment("Cooperative timeout for joint loot data preparation.")
                .defineInRange("prepare_timeout_seconds", 60, 1, 3_600);
        MAX_PREDICATES = builder
                .defineInRange("max_predicates", 100_000, 1, 1_000_000);
        MAX_ITEM_MODIFIERS = builder
                .defineInRange("max_item_modifiers", 100_000, 1, 1_000_000);
        MAX_LOOT_TABLES = builder
                .defineInRange("max_loot_tables", 100_000, 1, 1_000_000);
        MAX_TOTAL_JSON_BYTES = builder
                .defineInRange("max_total_json_bytes", 268_435_456L, 1L, 2_147_483_647L);
        MAX_DEPENDENCY_EDGES = builder
                .defineInRange("max_dependency_edges", 1_000_000, 1, 10_000_000);
        builder.pop();
        builder.push("recipes");
        MAX_RECIPES = builder.defineInRange("max_recipes", 100_000, 1, 1_000_000);
        MAX_RECIPE_JSON_BYTES = builder.defineInRange("max_total_json_bytes", 268_435_456L, 1L, 2_147_483_647L);
        builder.pop();
        builder.push("kubejs_recipes");
        KUBEJS_PREPARE_TIMEOUT_SECONDS = builder.defineInRange("prepare_timeout_seconds", 90, 1, 3_600);
        KUBEJS_MAX_SCRIPT_FILES = builder.defineInRange("max_script_files", 10_000, 1, 1_000_000);
        KUBEJS_MAX_SCRIPT_BYTES = builder.defineInRange("max_total_script_bytes", 67_108_864L, 1L, 2_147_483_647L);
        KUBEJS_MAX_MUTATIONS = builder.defineInRange("max_recipe_mutations", 1_000_000, 1, 10_000_000);
        KUBEJS_MAX_FINAL_RECIPES = builder.defineInRange("max_final_recipes", 100_000, 1, 1_000_000);
        KUBEJS_MAX_DEPENDENCY_EDGES = builder.defineInRange("max_script_dependency_edges", 100_000, 1, 10_000_000);
        builder.pop();
        builder.push("tags");
        TAG_PREPARE_TIMEOUT_SECONDS = builder.defineInRange("prepare_timeout_seconds", 60, 1, 3_600);
        MAX_TAG_FILES = builder.defineInRange("max_tag_files", 100_000, 1, 1_000_000);
        MAX_TAGS = builder.defineInRange("max_tags", 100_000, 1, 1_000_000);
        MAX_TAG_ENTRIES = builder.defineInRange("max_entries", 1_000_000, 1, 10_000_000);
        MAX_TAG_DEPENDENCY_EDGES = builder.defineInRange("max_dependency_edges", 2_000_000, 1, 20_000_000);
        MAX_TAG_JSON_BYTES = builder.defineInRange("max_total_json_bytes", 268_435_456L, 1L, 2_147_483_647L);
        builder.pop();
        builder.pop();
        builder.push("client_sync");
        CLIENT_SYNC_HANDSHAKE_TIMEOUT_TICKS = builder
                .comment("Ticks allowed for the optional client handshake.")
                .defineInRange("handshake_timeout_ticks", 200, 20, 1_200);
        builder.pop();

        builder.push("experimental");
        ENABLE_UNKNOWN_RESOURCE_REPORTING = builder
                .comment("Include unknown server-data resources in operator reports.")
                .define("enable_unknown_resource_reporting", true);
        builder.pop();

        SPEC = builder.build();
    }

    private PartialReloadConfig() {
    }

    public static int commandPermissionLevel() {
        return COMMAND_PERMISSION_LEVEL.get();
    }

    public static boolean logResourceDetails() {
        return LOG_RESOURCE_DETAILS.get();
    }

    public static int maxScannedResources() {
        return MAX_SCANNED_RESOURCES.get();
    }

    public static int scanTimeoutSeconds() {
        return SCAN_TIMEOUT_SECONDS.get();
    }

    public static boolean enableUnknownResourceReporting() {
        return ENABLE_UNKNOWN_RESOURCE_REPORTING.get();
    }

    public static int prepareTimeoutSeconds() {
        return PREPARE_TIMEOUT_SECONDS.get();
    }

    public static int maxFunctionCount() {
        return MAX_FUNCTION_COUNT.get();
    }

    public static int maxFunctionLines() {
        return MAX_FUNCTION_LINES.get();
    }

    public static int lootPrepareTimeoutSeconds() {
        return LOOT_PREPARE_TIMEOUT_SECONDS.get();
    }

    public static int maxPredicates() {
        return MAX_PREDICATES.get();
    }

    public static int maxItemModifiers() {
        return MAX_ITEM_MODIFIERS.get();
    }

    public static int maxLootTables() {
        return MAX_LOOT_TABLES.get();
    }

    public static long maxTotalJsonBytes() {
        return MAX_TOTAL_JSON_BYTES.get();
    }

    public static int maxDependencyEdges() {
        return MAX_DEPENDENCY_EDGES.get();
    }

    public static int maxRecipes() { return MAX_RECIPES.get(); }
    public static long maxRecipeJsonBytes() { return MAX_RECIPE_JSON_BYTES.get(); }
    public static int kubeJsPrepareTimeoutSeconds() { return KUBEJS_PREPARE_TIMEOUT_SECONDS.get(); }
    public static int kubeJsMaxScriptFiles() { return KUBEJS_MAX_SCRIPT_FILES.get(); }
    public static long kubeJsMaxScriptBytes() { return KUBEJS_MAX_SCRIPT_BYTES.get(); }
    public static int kubeJsMaxMutations() { return KUBEJS_MAX_MUTATIONS.get(); }
    public static int kubeJsMaxFinalRecipes() { return KUBEJS_MAX_FINAL_RECIPES.get(); }
    public static int kubeJsMaxDependencyEdges() { return KUBEJS_MAX_DEPENDENCY_EDGES.get(); }
    public static int tagPrepareTimeoutSeconds() { return TAG_PREPARE_TIMEOUT_SECONDS.get(); }
    public static int maxTagFiles() { return MAX_TAG_FILES.get(); }
    public static int maxTags() { return MAX_TAGS.get(); }
    public static int maxTagEntries() { return MAX_TAG_ENTRIES.get(); }
    public static int maxTagDependencyEdges() { return MAX_TAG_DEPENDENCY_EDGES.get(); }
    public static long maxTagJsonBytes() { return MAX_TAG_JSON_BYTES.get(); }
    public static int clientSyncHandshakeTimeoutTicks() {
        return CLIENT_SYNC_HANDSHAKE_TIMEOUT_TICKS.get();
    }
}
