package com.gabriel0liv.partialreload.function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.ScheduleCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class VanillaFunctionsProviderTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private final VanillaFunctionsProvider provider =
            new VanillaFunctionsProvider(new com.gabriel0liv.partialreload.resource.ResourceScanner(Clock.systemUTC()));

    @Test
    void preparesValidImmutableGenerationWithExactSnapshotAndDependencies() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("base", "test:functions/caller.mcfunction", """
                        function test:target
                        function #test:group
                        schedule function test:target 1t
                        """)
                .put("base", "test:functions/target.mcfunction", "noop\n")
                .put("base", "test:tags/functions/group.json", """
                        {"values":["test:target"]}
                        """)
                .put("base", "minecraft:tags/functions/tick.json", """
                        {"values":["test:caller"]}
                        """)
                .put("base", "minecraft:tags/functions/load.json", """
                        {"values":["test:target"]}
                        """);

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertTrue(artifact.isApplicable());
        assertEquals(ID, artifact.preparationId());
        assertEquals(3, artifact.functions().size());
        assertEquals(Set.of(id("test:caller")), artifact.tickFunctions());
        assertEquals(Set.of(id("test:target")), artifact.loadFunctions());
        assertTrue(artifact.dependencyGraph().dependencies().stream().anyMatch(edge ->
                edge.type() == FunctionDependencyType.DIRECT_FUNCTION_CALL
                        && edge.target().equals(id("test:target"))));
        assertTrue(artifact.dependencyGraph().dependencies().stream().anyMatch(edge ->
                edge.type() == FunctionDependencyType.FUNCTION_TAG_CALL
                        && edge.target().equals(id("test:group"))));
        assertTrue(artifact.dependencyGraph().dependencies().stream().anyMatch(edge ->
                edge.type() == FunctionDependencyType.SCHEDULED_FUNCTION_CALL
                        && edge.target().equals(id("test:target"))));
        assertFalse(artifact.sourceSnapshot().resources().isEmpty());
        assertThrows(UnsupportedOperationException.class, () ->
                artifact.functions().put(id("test:new"), artifact.functions().values().iterator().next()));
        assertThrows(UnsupportedOperationException.class, () ->
                artifact.functionTags().get(id("test:group")).add(id("test:new")));
    }

    @Test
    void invalidOrUnknownCommandInvalidatesWholeArtifactWithLineContext() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/bad.mcfunction", "noop\nunknown_mod_command value\n");

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertFalse(artifact.isApplicable());
        assertFalse(artifact.functions().containsKey(id("test:bad")));
        var issue = artifact.validation().issues().stream()
                .filter(value -> value.code().equals("FUNCTION_COMMAND_ERROR"))
                .findFirst().orElseThrow();
        assertEquals(2, issue.sourceLocation().line());
        assertEquals("unknown_mod_command value", issue.sourceLocation().command());
        assertEquals("pack", issue.packId());
    }

    @Test
    void detectsMissingDirectAndTagReferences() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/caller.mcfunction", """
                        function test:missing
                        function #test:missing_tag
                        """);

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertFalse(artifact.isApplicable());
        assertTrue(hasCode(artifact, "FUNCTION_REFERENCE_MISSING"));
        assertTrue(hasCode(artifact, "FUNCTION_TAG_REFERENCE_MISSING"));
    }

    @Test
    void mergesReplacesAndResolvesFunctionTagsByPackOrder() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("base", "test:functions/a.mcfunction", "noop")
                .put("base", "test:functions/b.mcfunction", "noop")
                .put("base", "test:tags/functions/group.json", """
                        {"values":["test:a"]}
                        """)
                .put("override", "test:tags/functions/group.json", """
                        {"replace":true,"values":["test:b"]}
                        """)
                .put("base", "test:tags/functions/outer.json", """
                        {"values":["#test:group",{"id":"test:missing","required":false}]}
                        """);

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertEquals(Set.of(id("test:b")), artifact.functionTags().get(id("test:group")));
        assertEquals(Set.of(id("test:b")), artifact.functionTags().get(id("test:outer")));
        assertTrue(artifact.isApplicable());
    }

    @Test
    void detectsTickLoadChangesAndTagCycles() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/a.mcfunction", "noop")
                .put("pack", "minecraft:tags/functions/tick.json", """
                        {"values":["test:a"]}
                        """)
                .put("pack", "minecraft:tags/functions/load.json", """
                        {"values":["test:a"]}
                        """)
                .put("pack", "test:tags/functions/one.json", """
                        {"values":["#test:two"]}
                        """)
                .put("pack", "test:tags/functions/two.json", """
                        {"values":["#test:one"]}
                        """);

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertTrue(artifact.tickDelta().changed());
        assertTrue(artifact.loadDelta().changed());
        assertTrue(hasCode(artifact, "TICK_FUNCTION_SET_CHANGED"));
        assertTrue(hasCode(artifact, "LOAD_FUNCTION_SET_CHANGED"));
        assertTrue(hasCode(artifact, "FUNCTION_TAG_CYCLE"));
        assertFalse(artifact.isApplicable());
    }

    @Test
    void detectsFunctionRecursionAsWarningNotError() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/a.mcfunction", "function test:b")
                .put("pack", "test:functions/b.mcfunction", "function test:a");

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertTrue(artifact.isApplicable());
        assertTrue(hasCode(artifact, "FUNCTION_RECURSION_DETECTED"));
        assertEquals(1, artifact.validation().count(ValidationSeverity.WARNING));
    }

    @Test
    void invalidatesWhenResourcesChangeDuringPreparation() throws Exception {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/a.mcfunction", "noop");
        resources.mutateAfterFirstStackListing(() ->
                resources.replaceWinner("test:functions/a.mcfunction", "noop\nnoop"));

        PreparedFunctions artifact = provider.prepare(context(resources));

        assertFalse(artifact.isApplicable());
        assertTrue(hasCode(artifact, "RESOURCE_CHANGED_DURING_PREPARATION"));
    }

    @Test
    void enforcesCooperativeTimeout() {
        InMemoryResourceManager resources = baseResources()
                .put("pack", "test:functions/a.mcfunction", "noop");
        AtomicLong nanos = new AtomicLong();
        FunctionPreparationContext context = context(
                resources,
                Duration.ofNanos(1),
                () -> nanos.getAndAdd(2)
        );

        FunctionPreparationException exception = assertThrows(
                FunctionPreparationException.class,
                () -> provider.prepare(context)
        );
        assertEquals("PREPARATION_TIMEOUT", exception.code());
    }

    private static InMemoryResourceManager baseResources() {
        return new InMemoryResourceManager()
                .put("base", "test:functions/noop.mcfunction", "noop");
    }

    private static FunctionPreparationContext context(InMemoryResourceManager resources) {
        return context(resources, Duration.ofSeconds(10), System::nanoTime);
    }

    private static FunctionPreparationContext context(
            InMemoryResourceManager resources,
            Duration timeout,
            java.util.function.LongSupplier nanos
    ) {
        return new FunctionPreparationContext(
                resources,
                dispatcher(),
                2,
                Set.of(),
                Set.of(),
                timeout,
                1_000,
                1_000,
                10_000,
                Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC),
                () -> ID,
                nanos
        );
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("noop").executes(context -> 1));
        dispatcher.register(Commands.literal("echo")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(context -> 1)));
        FunctionCommand.register(dispatcher);
        ScheduleCommand.register(dispatcher);
        return dispatcher;
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static boolean hasCode(PreparedFunctions artifact, String code) {
        return artifact.validation().issues().stream().anyMatch(issue -> issue.code().equals(code));
    }
}
