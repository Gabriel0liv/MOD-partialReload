package com.gabriel0liv.partialreload;

import com.gabriel0liv.partialreload.command.PartialReloadCommand;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.ProviderRegistry;
import com.gabriel0liv.partialreload.core.VanillaDatapackProvider;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.function.VanillaFunctionsProvider;
import com.gabriel0liv.partialreload.loot.VanillaLootDataProvider;
import com.gabriel0liv.partialreload.joint.TagRecipeFaultInjection;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.UUID;

@Mod(PartialReloadMod.MOD_ID)
public final class PartialReloadMod {
    public static final String MOD_ID = "partialreload";
    public static final String VERSION = "0.3.0-SNAPSHOT";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PartialReloadService service;

    public PartialReloadMod(FMLJavaModLoadingContext context) {
        TagRecipeFaultInjection.clear();
        context.registerConfig(ModConfig.Type.COMMON, PartialReloadConfig.SPEC);

        Clock clock = Clock.systemUTC();
        ProviderRegistry registry = new ProviderRegistry();
        ResourceScanner scanner = new ResourceScanner(clock);
        VanillaDatapackProvider provider = new VanillaDatapackProvider(scanner);
        VanillaFunctionsProvider functionsProvider = new VanillaFunctionsProvider(scanner);
        VanillaLootDataProvider lootDataProvider = new VanillaLootDataProvider(scanner);
        registry.register(provider);
        registry.register(functionsProvider);
        registry.register(lootDataProvider);
        this.service = new PartialReloadService(
                registry,
                provider,
                new ReloadPlanner(clock, UUID::randomUUID),
                functionsProvider,
                lootDataProvider
        );

        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::serverTick);
    }

    private void serverStopping(ServerStoppingEvent event) {
        TagRecipeFaultInjection.clear();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        PartialReloadCommand.register(event.getDispatcher(), service);
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            service.processFunctionSafePoint(event.getServer());
            service.processTagRecipeSafePoint(event.getServer());
        }
    }

    public PartialReloadService service() {
        return service;
    }
}
