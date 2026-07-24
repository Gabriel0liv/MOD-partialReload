package com.gabriel0liv.partialreload.function;

import com.google.common.collect.ImmutableMap;
import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FunctionLibraryBridge {
    private FunctionLibraryBridge() {
    }

    public static ServerFunctionLibrary buildCandidate(PreparedFunctions artifact) {
        if (artifact.dispatcher() == null || artifact.compilationPermissionLevel() < 0) {
            throw new IllegalArgumentException("Prepared artifact lacks production dispatcher metadata");
        }
        ServerFunctionLibrary candidate = new ServerFunctionLibrary(
                artifact.compilationPermissionLevel(), artifact.dispatcher());
        Map<ResourceLocation, CommandFunction> functions = new LinkedHashMap<>();
        artifact.functions().forEach((id, prepared) -> functions.put(id, prepared.compiled()));
        Map<ResourceLocation, Collection<CommandFunction>> tags = new LinkedHashMap<>();
        artifact.functionTags().forEach((id, members) -> tags.put(id, members.stream()
                .map(functions::get).filter(java.util.Objects::nonNull).toList()));
        candidate.functions = ImmutableMap.copyOf(functions);
        candidate.tags = Map.copyOf(tags);
        return candidate;
    }

    public static ServerFunctionLibrary activeLibrary(ServerFunctionManager manager) {
        return manager.library;
    }

    public static boolean executionActive(ServerFunctionManager manager) {
        return manager.context != null;
    }

    public static void publishWithoutLoad(
            ServerFunctionManager manager,
            ServerFunctionLibrary library
    ) {
        manager.replaceLibrary(library);
        manager.postReload = false;
    }

    public static boolean loadPending(ServerFunctionManager manager) {
        return manager.postReload;
    }

    public static List<ResourceLocation> ticking(ServerFunctionManager manager) {
        return manager.ticking.stream().map(CommandFunction::getId).toList();
    }
}
