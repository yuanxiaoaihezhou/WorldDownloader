package com.worlddownloader;

import com.mojang.logging.LogUtils;
import com.worlddownloader.commands.WDLCommands;
import com.worlddownloader.config.WDLConfig;
import com.worlddownloader.download.WorldDownloadManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Main mod class for World Downloader.
 * This mod allows players to download server world chunks to their local machine.
 * Supports both vanilla and modded servers.
 */
@Mod(WorldDownloader.MODID)
public class WorldDownloader {
    public static final String MODID = "worlddownloader";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldDownloader() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WDLConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("World Downloader mod initialized for Minecraft 1.20.1");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        WDLCommands.register(event.getDispatcher());
        LOGGER.info("World Downloader commands registered");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("World Downloader client setup complete");
            WorldDownloadManager.init();
        }
    }
}
