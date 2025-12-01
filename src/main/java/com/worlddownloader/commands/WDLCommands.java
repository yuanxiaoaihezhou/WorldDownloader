package com.worlddownloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.worlddownloader.download.WorldDownloadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Registers and handles World Downloader commands.
 */
public class WDLCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("wdl")
                .then(Commands.literal("start")
                    .executes(context -> {
                        startDownload(null);
                        return 1;
                    })
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            startDownload(name);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("stop")
                    .executes(context -> {
                        stopDownload();
                        return 1;
                    })
                )
                .then(Commands.literal("status")
                    .executes(context -> {
                        showStatus();
                        return 1;
                    })
                )
                .then(Commands.literal("capture")
                    .executes(context -> {
                        captureNow();
                        return 1;
                    })
                )
                .then(Commands.literal("help")
                    .executes(context -> {
                        showHelp();
                        return 1;
                    })
                )
        );
        
        LOGGER.info("Registered WDL commands");
    }
    
    private static void startDownload(String name) {
        if (WorldDownloadManager.isDownloading()) {
            sendMessage("§c[WDL] Download already in progress! Use /wdl stop first.");
            return;
        }
        
        WorldDownloadManager.startDownload(name);
        String worldName = WorldDownloadManager.getWorldName();
        sendMessage("§a[WDL] Started downloading world: " + worldName);
        sendMessage("§7[WDL] Explore the world to download chunks. Use /wdl stop when done.");
    }
    
    private static void stopDownload() {
        if (!WorldDownloadManager.isDownloading()) {
            sendMessage("§c[WDL] No download in progress!");
            return;
        }
        
        int chunkCount = WorldDownloadManager.getDownloadedChunkCount();
        String worldName = WorldDownloadManager.getWorldName();
        
        WorldDownloadManager.stopDownload();
        
        sendMessage("§a[WDL] Download complete!");
        sendMessage("§7[WDL] Saved " + chunkCount + " chunks to: " + worldName);
        sendMessage("§7[WDL] Find your world in the singleplayer menu.");
    }
    
    private static void showStatus() {
        if (WorldDownloadManager.isDownloading()) {
            int chunks = WorldDownloadManager.getDownloadedChunkCount();
            String worldName = WorldDownloadManager.getWorldName();
            sendMessage("§a[WDL] Downloading: " + worldName);
            sendMessage("§7[WDL] Chunks captured: " + chunks);
        } else {
            sendMessage("§7[WDL] Not currently downloading.");
            sendMessage("§7[WDL] Use /wdl start to begin downloading.");
        }
    }
    
    private static void captureNow() {
        if (!WorldDownloadManager.isDownloading()) {
            sendMessage("§c[WDL] Start a download first with /wdl start");
            return;
        }
        
        int before = WorldDownloadManager.getDownloadedChunkCount();
        WorldDownloadManager.captureLoadedChunks();
        int after = WorldDownloadManager.getDownloadedChunkCount();
        int captured = after - before;
        
        sendMessage("§a[WDL] Captured " + captured + " new chunks. Total: " + after);
    }
    
    private static void showHelp() {
        sendMessage("§6=== World Downloader Help ===");
        sendMessage("§e/wdl start [name]§7 - Start downloading (optionally with a name)");
        sendMessage("§e/wdl stop§7 - Stop downloading and save the world");
        sendMessage("§e/wdl status§7 - Show download status");
        sendMessage("§e/wdl capture§7 - Manually capture all loaded chunks");
        sendMessage("§e/wdl help§7 - Show this help message");
        sendMessage("§7");
        sendMessage("§7This mod supports both vanilla and modded servers.");
        sendMessage("§7Modded blocks and entities will be saved correctly.");
    }
    
    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
