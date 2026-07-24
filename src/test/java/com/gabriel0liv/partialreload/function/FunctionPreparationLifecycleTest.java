package com.gabriel0liv.partialreload.function;

import com.mojang.brigadier.CommandDispatcher;
import com.gabriel0liv.partialreload.core.InvalidStateTransitionException;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.PartialReloadState;
import com.gabriel0liv.partialreload.core.ProviderRegistry;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.commands.FunctionCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class FunctionPreparationLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Test
    void rejectsConcurrentPreparationAndSupportsDiscard() {
        InMemoryResourceManager resources = new InMemoryResourceManager()
                .put("pack", "test:functions/a.mcfunction", "noop");
        VanillaFunctionsProvider provider =
                new VanillaFunctionsProvider(new ResourceScanner(CLOCK));
        PartialReloadService service = service(provider);
        QueueExecutor worker = new QueueExecutor();

        var future = service.prepareFunctionsAsync(
                context(resources, dispatcher(), Duration.ofSeconds(10), System::nanoTime),
                worker,
                Runnable::run
        );
        assertEquals(PartialReloadState.PREPARING, service.status().state());
        assertThrows(InvalidStateTransitionException.class, () ->
                service.prepareFunctionsAsync(
                        context(resources, dispatcher(), Duration.ofSeconds(10), System::nanoTime),
                        worker,
                        Runnable::run
                ));

        worker.runNext();
        assertTrue(future.isDone());
        assertEquals(PartialReloadState.READY, service.status().state());
        assertNotNull(service.preparedFunctions());
        assertTrue(service.discardPrepared());
        assertNull(service.preparedFunctions());
        assertEquals(PartialReloadState.IDLE, service.status().state());
    }

    @Test
    void infrastructureFailureAlwaysEndsFailedSafe() {
        InMemoryResourceManager resources = new InMemoryResourceManager()
                .put("pack", "test:functions/a.mcfunction", "noop");
        VanillaFunctionsProvider provider =
                new VanillaFunctionsProvider(new ResourceScanner(CLOCK));
        PartialReloadService service = service(provider);
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();

        var future = service.prepareFunctionsAsync(
                context(resources, dispatcher(), Duration.ofNanos(1), () -> nanos.getAndAdd(2)),
                Runnable::run,
                Runnable::run
        );

        assertThrows(java.util.concurrent.CompletionException.class, future::join);
        assertEquals(PartialReloadState.FAILED_SAFE, service.status().state());
        assertNull(service.preparedFunctions());
    }

    private static PartialReloadService service(VanillaFunctionsProvider provider) {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(provider);
        return new PartialReloadService(
                registry,
                provider,
                new ReloadPlanner(CLOCK, UUID::randomUUID),
                provider
        );
    }

    private static FunctionPreparationContext context(
            InMemoryResourceManager resources,
            CommandDispatcher<CommandSourceStack> dispatcher,
            Duration timeout,
            java.util.function.LongSupplier nanos
    ) {
        return new FunctionPreparationContext(
                resources,
                dispatcher,
                2,
                Set.of(),
                Set.of(),
                timeout,
                1_000,
                1_000,
                10_000,
                CLOCK,
                UUID::randomUUID,
                nanos
        );
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("noop").executes(context -> 1));
        FunctionCommand.register(dispatcher);
        return dispatcher;
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }
}
