package com.gabriel0liv.partialreload.function;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.validation.SourceLocation;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FunctionCompiler {
    Result compile(
            FunctionPreparationContext context,
            long startedAt,
            Map<ResourceLocation, FunctionResourceView.FunctionSource> sources
    ) throws FunctionPreparationException {
        CommandSourceStack source = new CommandSourceStack(
                CommandSource.NULL,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                context.compilationPermissionLevel(),
                "",
                CommonComponents.EMPTY,
                null,
                null
        );
        List<ValidationIssue> issues = new ArrayList<>();
        List<FunctionDependency> dependencies = new ArrayList<>();
        java.util.LinkedHashMap<ResourceLocation, PreparedFunction> compiled = new java.util.LinkedHashMap<>();

        for (FunctionResourceView.FunctionSource function : sources.values()) {
            FunctionResourceLoader.checkDeadline(context, startedAt);
            List<CommandFunction.Entry> entries = new ArrayList<>();
            List<String> commands = new ArrayList<>();
            boolean valid = true;
            for (int index = 0; index < function.lines().size(); index++) {
                String command = function.lines().get(index).trim();
                if (command.isEmpty() || command.startsWith("#")) continue;
                int line = index + 1;
                try {
                    if (command.startsWith("/")) {
                        String suggestion = command.length() > 1
                                ? new StringReader(command.substring(1)).readUnquotedString()
                                : "";
                        throw new IllegalArgumentException(
                                command.startsWith("//")
                                        ? "Unknown or invalid command; comments use '#', not '//'"
                                        : "Do not use a preceding slash"
                                                + (suggestion.isEmpty() ? "" : "; did you mean '" + suggestion + "'?")
                        );
                    }
                    StringReader reader = new StringReader(command);
                    ParseResults<CommandSourceStack> parse = context.dispatcher().parse(reader, source);
                    if (parse.getReader().canRead()) throw Commands.getParseException(parse);
                    entries.add(new CommandFunction.CommandEntry(parse));
                    commands.add(command);
                    extractDependencies(function.id(), command, line, parse.getContext(), false, dependencies);
                } catch (CommandSyntaxException exception) {
                    valid = false;
                    int cursor = exception.getCursor();
                    issues.add(commandIssue(
                            function,
                            line,
                            command,
                            cursor >= 0 ? cursor + 1 : null,
                            cursor >= 0 ? cursor : null,
                            exception.getRawMessage().getString(),
                            exception.toString()
                    ));
                } catch (RuntimeException exception) {
                    valid = false;
                    issues.add(commandIssue(
                            function,
                            line,
                            command,
                            null,
                            null,
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                            exception.toString()
                    ));
                }
            }
            if (valid) {
                CommandFunction candidate = new CommandFunction(
                        function.id(),
                        entries.toArray(CommandFunction.Entry[]::new)
                );
                compiled.put(function.id(), new PreparedFunction(
                        function.id(),
                        function.file(),
                        function.packId(),
                        candidate,
                        commands
                ));
            }
        }
        return new Result(compiled, dependencies, issues);
    }

    private static void extractDependencies(
            ResourceLocation source,
            String command,
            int line,
            CommandContextBuilder<CommandSourceStack> context,
            boolean scheduledParent,
            List<FunctionDependency> dependencies
    ) {
        boolean scheduled = scheduledParent || context.getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals("schedule"));
        for (ParsedArgument<CommandSourceStack, ?> argument : context.getArguments().values()) {
            if (!(argument.getResult() instanceof FunctionArgument.Result)) continue;
            int start = argument.getRange().getStart();
            int end = argument.getRange().getEnd();
            if (start < 0 || end > command.length() || start >= end) continue;
            String token = command.substring(start, end);
            boolean tag = token.startsWith("#");
            String rawId = tag ? token.substring(1) : token;
            ResourceLocation target = ResourceLocation.tryParse(rawId);
            if (target == null) continue;
            FunctionDependencyType type = scheduled
                    ? FunctionDependencyType.SCHEDULED_FUNCTION_CALL
                    : tag
                    ? FunctionDependencyType.FUNCTION_TAG_CALL
                    : FunctionDependencyType.DIRECT_FUNCTION_CALL;
            dependencies.add(new FunctionDependency(source, target, type, line, tag));
        }
        if (context.getChild() != null) {
            extractDependencies(source, command, line, context.getChild(), scheduled, dependencies);
        }
    }

    private static ValidationIssue commandIssue(
            FunctionResourceView.FunctionSource function,
            int line,
            String command,
            Integer column,
            Integer cursor,
            String message,
            String cause
    ) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                "FUNCTION_COMMAND_ERROR",
                ReloadCategory.FUNCTIONS,
                VanillaFunctionsProvider.ID,
                function.id(),
                function.packId(),
                message,
                new SourceLocation(function.file(), line, column, cursor, command),
                cause
        );
    }

    record Result(
            Map<ResourceLocation, PreparedFunction> functions,
            List<FunctionDependency> dependencies,
            List<ValidationIssue> issues
    ) {
        Result {
            functions = Map.copyOf(functions);
            dependencies = List.copyOf(dependencies);
            issues = List.copyOf(issues);
        }
    }
}
