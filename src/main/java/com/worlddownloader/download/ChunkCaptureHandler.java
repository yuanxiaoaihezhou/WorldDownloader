package com.worlddownloader.download;

import com.mojang.logging.LogUtils;
import com.worlddownloader.WorldDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Event handler for capturing chunks as they are loaded.
 * Automatically captures chunks when download mode is enabled.
 */
@Mod.EventBusSubscriber(modid = WorldDownloader.MODID, value = Dist.CLIENT)
public class ChunkCaptureHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!WorldDownloadManager.isDownloading()) {
            return;
        }
        
        if (event.getLevel() instanceof ClientLevel && event.getChunk() instanceof LevelChunk chunk) {
            WorldDownloadManager.captureChunk(chunk);
        }
    }
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        if (!WorldDownloadManager.isDownloading()) {
            return;
        }
        
        // Periodically capture all loaded chunks
        tickCounter++;
        if (tickCounter >= 100) { // Every 5 seconds (20 ticks per second)
            tickCounter = 0;
            WorldDownloadManager.captureLoadedChunks();
        }
    }
}
