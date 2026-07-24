package com.gabriel0liv.partialreload.function;

import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class FunctionLibraryBridge {
    private FunctionLibraryBridge() {
    }

    public static ServerFunctionLibrary buildCandidate(PreparedFunctions artifact) {
        throw unsupported();
    }

    public static ServerFunctionLibrary activeLibrary(ServerFunctionManager manager) {
        throw unsupported();
    }

    public static boolean executionActive(ServerFunctionManager manager) {
        throw unsupported();
    }

    public static void publishWithoutLoad(
            ServerFunctionManager manager,
            ServerFunctionLibrary library
    ) {
        throw unsupported();
    }

    public static boolean loadPending(ServerFunctionManager manager) {
        throw unsupported();
    }

    public static List<ResourceLocation> ticking(ServerFunctionManager manager) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "Function manager bridge is reserved for a future commit specification");
    }
}
