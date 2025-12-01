package com.worlddownloader.config;

import com.worlddownloader.WorldDownloader;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Configuration for World Downloader mod.
 */
@Mod.EventBusSubscriber(modid = WorldDownloader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class WDLConfig {
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    private static final ForgeConfigSpec.BooleanValue CAPTURE_ENTITIES = BUILDER
            .comment("Whether to capture entities when downloading chunks")
            .define("captureEntities", true);
    
    private static final ForgeConfigSpec.BooleanValue CAPTURE_BLOCK_ENTITIES = BUILDER
            .comment("Whether to capture block entities (chests, signs, etc.)")
            .define("captureBlockEntities", true);
    
    private static final ForgeConfigSpec.IntValue AUTO_SAVE_INTERVAL = BUILDER
            .comment("How often to auto-save chunks (in seconds, 0 to disable)")
            .defineInRange("autoSaveInterval", 60, 0, 600);
    
    private static final ForgeConfigSpec.IntValue CHUNKS_BEFORE_SAVE = BUILDER
            .comment("Number of chunks to capture before auto-saving")
            .defineInRange("chunksBeforeSave", 32, 1, 256);
    
    private static final ForgeConfigSpec.BooleanValue CAPTURE_MODDED = BUILDER
            .comment("Whether to capture modded blocks and entities")
            .define("captureModded", true);
    
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    
    public static boolean captureEntities;
    public static boolean captureBlockEntities;
    public static int autoSaveInterval;
    public static int chunksBeforeSave;
    public static boolean captureModded;
    
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        captureEntities = CAPTURE_ENTITIES.get();
        captureBlockEntities = CAPTURE_BLOCK_ENTITIES.get();
        autoSaveInterval = AUTO_SAVE_INTERVAL.get();
        chunksBeforeSave = CHUNKS_BEFORE_SAVE.get();
        captureModded = CAPTURE_MODDED.get();
    }
}
