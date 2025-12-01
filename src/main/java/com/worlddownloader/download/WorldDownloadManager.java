package com.worlddownloader.download;

import com.mojang.logging.LogUtils;
import com.worlddownloader.WorldDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the world download process.
 * Handles capturing chunk data, block entities, and entities from the server.
 * Supports modded blocks and entities through registry lookups.
 */
public class WorldDownloadManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static boolean downloading = false;
    private static String worldName = "downloaded_world";
    private static Path savePath;
    private static final Set<ChunkPos> downloadedChunks = ConcurrentHashMap.newKeySet();
    private static final Map<ChunkPos, ChunkData> chunkDataMap = new ConcurrentHashMap<>();
    
    public static void init() {
        LOGGER.info("WorldDownloadManager initialized");
    }
    
    public static boolean isDownloading() {
        return downloading;
    }
    
    public static void startDownload(String name) {
        if (downloading) {
            LOGGER.warn("Download already in progress");
            return;
        }
        
        worldName = name != null && !name.isEmpty() ? name : "downloaded_world_" + System.currentTimeMillis();
        
        // Create save directory
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        savePath = gameDir.resolve("saves").resolve(worldName);
        
        try {
            Files.createDirectories(savePath);
            Files.createDirectories(savePath.resolve("region"));
            Files.createDirectories(savePath.resolve("entities"));
            Files.createDirectories(savePath.resolve("data"));
            
            // Create level.dat
            createLevelDat();
            
            downloading = true;
            downloadedChunks.clear();
            chunkDataMap.clear();
            
            LOGGER.info("Started world download: {}", worldName);
        } catch (IOException e) {
            LOGGER.error("Failed to create save directory", e);
        }
    }
    
    public static void stopDownload() {
        if (!downloading) {
            LOGGER.warn("No download in progress");
            return;
        }
        
        // Save all pending chunks
        saveAllChunks();
        
        downloading = false;
        LOGGER.info("Stopped world download. Saved {} chunks", downloadedChunks.size());
    }
    
    public static void captureChunk(LevelChunk chunk) {
        if (!downloading || chunk == null) {
            return;
        }
        
        ChunkPos pos = chunk.getPos();
        if (downloadedChunks.contains(pos)) {
            return;
        }
        
        try {
            ChunkData data = new ChunkData(chunk);
            chunkDataMap.put(pos, data);
            downloadedChunks.add(pos);
            
            LOGGER.debug("Captured chunk at ({}, {})", pos.x, pos.z);
            
            // Periodically save chunks to prevent memory overflow
            if (chunkDataMap.size() >= 32) {
                saveAllChunks();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to capture chunk at ({}, {})", pos.x, pos.z, e);
        }
    }
    
    public static void captureLoadedChunks() {
        if (!downloading) {
            return;
        }
        
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        
        int count = 0;
        for (ChunkAccess chunk : getLoadedChunks(level)) {
            if (chunk instanceof LevelChunk levelChunk) {
                captureChunk(levelChunk);
                count++;
            }
        }
        
        LOGGER.info("Captured {} loaded chunks", count);
    }
    
    private static List<ChunkAccess> getLoadedChunks(ClientLevel level) {
        List<ChunkAccess> chunks = new ArrayList<>();
        Player player = Minecraft.getInstance().player;
        if (player == null) return chunks;
        
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        int viewDistance = Minecraft.getInstance().options.renderDistance().get();
        
        for (int x = playerChunkX - viewDistance; x <= playerChunkX + viewDistance; x++) {
            for (int z = playerChunkZ - viewDistance; z <= playerChunkZ + viewDistance; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            }
        }
        
        return chunks;
    }
    
    private static void createLevelDat() throws IOException {
        CompoundTag levelData = new CompoundTag();
        CompoundTag dataTag = new CompoundTag();
        
        ClientLevel level = Minecraft.getInstance().level;
        String levelName = worldName;
        
        if (level != null) {
            // Copy relevant world data
            dataTag.putString("LevelName", levelName);
            dataTag.putLong("LastPlayed", System.currentTimeMillis());
            dataTag.putInt("version", 19133);
            dataTag.putInt("DataVersion", ChunkData.MC_1_20_1_DATA_VERSION);
            dataTag.putBoolean("allowCommands", true);
            dataTag.putInt("GameType", 0); // Survival
            dataTag.putBoolean("hardcore", false);
            dataTag.putInt("Difficulty", 2); // Normal
            dataTag.putBoolean("DifficultyLocked", false);;
            
            // World spawn
            dataTag.putInt("SpawnX", 0);
            dataTag.putInt("SpawnY", 64);
            dataTag.putInt("SpawnZ", 0);
            
            // Version info
            CompoundTag version = new CompoundTag();
            version.putInt("Id", ChunkData.MC_1_20_1_DATA_VERSION);
            version.putString("Name", "1.20.1");
            version.putBoolean("Snapshot", false);
            dataTag.put("Version", version);
            
            // World generation
            CompoundTag worldGenSettings = new CompoundTag();
            worldGenSettings.putLong("seed", 0);
            worldGenSettings.putBoolean("generate_features", true);
            dataTag.put("WorldGenSettings", worldGenSettings);
        }
        
        levelData.put("Data", dataTag);
        
        File levelDat = savePath.resolve("level.dat").toFile();
        NbtIo.writeCompressed(levelData, levelDat);
        
        LOGGER.info("Created level.dat for world: {}", levelName);
    }
    
    private static void saveAllChunks() {
        if (chunkDataMap.isEmpty()) {
            return;
        }
        
        // Group chunks by region
        Map<Long, List<Map.Entry<ChunkPos, ChunkData>>> regionChunks = new HashMap<>();
        
        for (Map.Entry<ChunkPos, ChunkData> entry : chunkDataMap.entrySet()) {
            ChunkPos pos = entry.getKey();
            int regionX = pos.x >> 5;
            int regionZ = pos.z >> 5;
            long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
            
            regionChunks.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(entry);
        }
        
        // Save each region
        for (Map.Entry<Long, List<Map.Entry<ChunkPos, ChunkData>>> regionEntry : regionChunks.entrySet()) {
            long regionKey = regionEntry.getKey();
            int regionX = (int) (regionKey >> 32);
            int regionZ = (int) regionKey;
            
            try {
                saveRegion(regionX, regionZ, regionEntry.getValue());
            } catch (Exception e) {
                LOGGER.error("Failed to save region ({}, {})", regionX, regionZ, e);
            }
        }
        
        chunkDataMap.clear();
    }
    
    private static void saveRegion(int regionX, int regionZ, List<Map.Entry<ChunkPos, ChunkData>> chunks) throws IOException {
        Path regionPath = savePath.resolve("region").resolve("r." + regionX + "." + regionZ + ".mca");
        
        try (RegionFileWriter writer = new RegionFileWriter(regionPath.toFile())) {
            for (Map.Entry<ChunkPos, ChunkData> entry : chunks) {
                ChunkPos pos = entry.getKey();
                ChunkData data = entry.getValue();
                
                CompoundTag chunkNbt = data.toNbt();
                writer.writeChunk(pos, chunkNbt);
            }
        }
        
        LOGGER.debug("Saved region ({}, {}) with {} chunks", regionX, regionZ, chunks.size());
    }
    
    public static int getDownloadedChunkCount() {
        return downloadedChunks.size();
    }
    
    public static String getWorldName() {
        return worldName;
    }
    
    public static Path getSavePath() {
        return savePath;
    }
}
