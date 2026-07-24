package com.gabriel0liv.partialreload;

import com.gabriel0liv.partialreload.command.PartialReloadCommand;
import com.gabriel0liv.partialreload.config.PartialReloadConfig;
import com.gabriel0liv.partialreload.core.PartialReloadService;
import com.gabriel0liv.partialreload.core.ProviderRegistry;
import com.gabriel0liv.partialreload.core.VanillaDatapackProvider;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.UUID;

@Mod(PartialReloadMod.MOD_ID)
public final class PartialReloadMod {
    public static final String MOD_ID = "partialreload";
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PartialReloadService service;

    public PartialReloadMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, PartialReloadConfig.SPEC);

        Clock clock = Clock.systemUTC();
        ProviderRegistry registry = new ProviderRegistry();
        VanillaDatapackProvider provider = new VanillaDatapackProvider(new ResourceScanner(clock));
        registry.register(provider);
        this.service = new PartialReloadService(
                registry,
                provider,
                new ReloadPlanner(clock, UUID::randomUUID)
        );

        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        PartialReloadCommand.register(event.getDispatcher(), service);
    }
}
