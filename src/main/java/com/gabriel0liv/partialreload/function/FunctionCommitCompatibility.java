package com.gabriel0liv.partialreload.function;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.util.Objects;

public record FunctionCommitCompatibility(
        FunctionCommitCompatibilityStatus status,
        String detail
) {
    public FunctionCommitCompatibility {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    public boolean compatible() {
        return status == FunctionCommitCompatibilityStatus.FUNCTION_COMMIT_COMPATIBLE;
    }

    public static FunctionCommitCompatibility inspect(MinecraftServer server) {
        try {
            String minecraft = SharedConstants.getCurrentVersion().getName();
            String forge = ForgeVersion.getVersion();
            if (!"1.20.1".equals(minecraft) || !"47.4.10".equals(forge)) {
                return new FunctionCommitCompatibility(
                        FunctionCommitCompatibilityStatus.FUNCTION_COMMIT_DISABLED_INCOMPATIBLE_TARGET,
                        "Expected Minecraft 1.20.1/Forge 47.4.10, found "
                                + minecraft + "/" + forge
                );
            }
            return new FunctionCommitCompatibility(
                    FunctionCommitCompatibilityStatus.FUNCTION_COMMIT_DISABLED_UNVERIFIED,
                    "Function manager access is intentionally unavailable during read-only preparation"
            );
        } catch (LinkageError | RuntimeException exception) {
            return new FunctionCommitCompatibility(
                    FunctionCommitCompatibilityStatus.FUNCTION_COMMIT_DISABLED_INCOMPATIBLE_TARGET,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }
}
