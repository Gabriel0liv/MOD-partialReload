package com.gabriel0liv.partialreload.function;

import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public final class PreparedFunction {
    private final ResourceLocation id;
    private final ResourceLocation sourceFile;
    private final String sourcePack;
    private final CommandFunction compiled;
    private final List<String> commands;

    PreparedFunction(
            ResourceLocation id,
            ResourceLocation sourceFile,
            String sourcePack,
            CommandFunction compiled,
            List<String> commands
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.sourcePack = Objects.requireNonNull(sourcePack, "sourcePack");
        this.compiled = Objects.requireNonNull(compiled, "compiled");
        this.commands = List.copyOf(commands);
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation sourceFile() {
        return sourceFile;
    }

    public String sourcePack() {
        return sourcePack;
    }

    public int commandCount() {
        return compiled.getEntries().length;
    }

    public List<String> commands() {
        return commands;
    }

    CommandFunction compiled() {
        return compiled;
    }
}
