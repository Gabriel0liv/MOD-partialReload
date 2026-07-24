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
}
