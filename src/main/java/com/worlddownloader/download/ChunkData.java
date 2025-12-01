package com.worlddownloader.download;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.*;

/**
 * Represents captured chunk data for serialization.
 * Supports both vanilla and modded blocks/entities.
 */
public class ChunkData {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** Minecraft 1.20.1 data version for NBT compatibility */
    public static final int MC_1_20_1_DATA_VERSION = 3465;
    
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final List<SectionData> sections = new ArrayList<>();
    private final List<CompoundTag> blockEntities = new ArrayList<>();
    private final List<CompoundTag> entities = new ArrayList<>();
    private final Map<Heightmap.Types, long[]> heightmaps = new HashMap<>();
    
    public ChunkData(LevelChunk chunk) {
        this.chunkX = chunk.getPos().x;
        this.chunkZ = chunk.getPos().z;
        this.minY = chunk.getMinBuildHeight();
        this.height = chunk.getHeight();
        
        // Capture sections
        LevelChunkSection[] levelSections = chunk.getSections();
        for (int i = 0; i < levelSections.length; i++) {
            LevelChunkSection section = levelSections[i];
            if (section != null && !section.hasOnlyAir()) {
                sections.add(new SectionData(section, chunk.getSectionYFromSectionIndex(i)));
            }
        }
        
        // Capture block entities (including modded ones)
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            try {
                BlockEntity be = entry.getValue();
                CompoundTag beTag = be.saveWithFullMetadata();
                
                // Ensure position is saved
                beTag.putInt("x", entry.getKey().getX());
                beTag.putInt("y", entry.getKey().getY());
                beTag.putInt("z", entry.getKey().getZ());
                
                // Save block entity type for modded support
                ResourceLocation beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                if (beType != null) {
                    beTag.putString("id", beType.toString());
                }
                
                blockEntities.add(beTag);
            } catch (Exception e) {
                LOGGER.warn("Failed to save block entity at {}", entry.getKey(), e);
            }
        }
        
        // Capture entities (including modded ones, excluding players)
        AABB chunkBB = new AABB(
            chunk.getPos().getMinBlockX(), minY, chunk.getPos().getMinBlockZ(),
            chunk.getPos().getMaxBlockX() + 1, minY + height, chunk.getPos().getMaxBlockZ() + 1
        );
        
        for (Entity entity : chunk.getLevel().getEntities(null, chunkBB, e -> !(e instanceof Player))) {
            try {
                CompoundTag entityTag = new CompoundTag();
                if (entity.save(entityTag)) {
                    // Ensure entity type is saved for modded support
                    ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    if (entityType != null) {
                        entityTag.putString("id", entityType.toString());
                    }
                    entities.add(entityTag);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to save entity {}", entity.getType(), e);
            }
        }
        
        // Capture heightmaps
        for (Heightmap.Types type : Heightmap.Types.values()) {
            if (type.sendToClient()) {
                Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
                if (heightmap != null) {
                    heightmaps.put(type, heightmap.getRawData());
                }
            }
        }
    }
    
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        
        // Chunk coordinates
        tag.putInt("xPos", chunkX);
        tag.putInt("zPos", chunkZ);
        tag.putInt("yPos", minY >> 4);
        
        // Data version for 1.20.1
        tag.putInt("DataVersion", MC_1_20_1_DATA_VERSION);
        
        // Status
        tag.putString("Status", "minecraft:full");
        
        // Last update time
        tag.putLong("LastUpdate", System.currentTimeMillis());
        
        // Sections
        ListTag sectionsTag = new ListTag();
        for (SectionData section : sections) {
            sectionsTag.add(section.toNbt());
        }
        tag.put("sections", sectionsTag);
        
        // Block entities
        ListTag beTag = new ListTag();
        beTag.addAll(blockEntities);
        tag.put("block_entities", beTag);
        
        // Heightmaps
        CompoundTag heightmapsTag = new CompoundTag();
        for (Map.Entry<Heightmap.Types, long[]> entry : heightmaps.entrySet()) {
            heightmapsTag.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue()));
        }
        tag.put("Heightmaps", heightmapsTag);
        
        // Post-processing - empty list for downloaded worlds
        ListTag postProcessing = new ListTag();
        for (int i = 0; i < 24; i++) {
            postProcessing.add(new ListTag());
        }
        tag.put("PostProcessing", postProcessing);
        
        return tag;
    }
    
    public ListTag getEntitiesNbt() {
        ListTag entitiesTag = new ListTag();
        entitiesTag.addAll(entities);
        return entitiesTag;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    /**
     * Represents a single chunk section (16x16x16 blocks).
     */
    private static class SectionData {
        /** Section size in each dimension */
        private static final int SECTION_SIZE = 16;
        /** Total blocks per section (16x16x16) */
        private static final int BLOCKS_PER_SECTION = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
        /** Biome resolution per section (4x4x4 grid) */
        private static final int BIOME_RESOLUTION = 4;
        /** Total biomes per section */
        private static final int BIOMES_PER_SECTION = BIOME_RESOLUTION * BIOME_RESOLUTION * BIOME_RESOLUTION;
        /** Default block light level */
        private static final byte DEFAULT_BLOCK_LIGHT = 0;
        /** Default sky light level (full daylight) */
        private static final byte DEFAULT_SKY_LIGHT = 15;
        
        private final int y;
        private final BlockState[] blocks = new BlockState[BLOCKS_PER_SECTION];
        private final Holder<Biome>[] biomes;
        private final byte blockLight;
        private final byte skyLight;
        
        @SuppressWarnings("unchecked")
        public SectionData(LevelChunkSection section, int y) {
            this.y = y;
            this.blockLight = DEFAULT_BLOCK_LIGHT;
            this.skyLight = DEFAULT_SKY_LIGHT;
            this.biomes = new Holder[BIOMES_PER_SECTION];
            
            // Copy block states
            for (int x = 0; x < SECTION_SIZE; x++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    for (int localY = 0; localY < SECTION_SIZE; localY++) {
                        int index = localY * SECTION_SIZE * SECTION_SIZE + z * SECTION_SIZE + x;
                        try {
                            blocks[index] = section.getBlockState(x, localY, z);
                        } catch (Exception e) {
                            blocks[index] = Blocks.AIR.defaultBlockState();
                        }
                    }
                }
            }
            
            // Copy biomes (4x4x4 grid)
            for (int bx = 0; bx < BIOME_RESOLUTION; bx++) {
                for (int bz = 0; bz < BIOME_RESOLUTION; bz++) {
                    for (int by = 0; by < BIOME_RESOLUTION; by++) {
                        int index = by * BIOME_RESOLUTION * BIOME_RESOLUTION + bz * BIOME_RESOLUTION + bx;
                        try {
                            biomes[index] = section.getNoiseBiome(bx, by, bz);
                        } catch (Exception e) {
                            biomes[index] = null;
                        }
                    }
                }
            }
        }
        
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putByte("Y", (byte) y);
            
            // Block states with palette
            CompoundTag blockStatesTag = new CompoundTag();
            ListTag palette = new ListTag();
            Map<BlockState, Integer> paletteMap = new LinkedHashMap<>();
            
            // Build palette (includes modded blocks via registry)
            for (BlockState state : blocks) {
                if (state != null && !paletteMap.containsKey(state)) {
                    paletteMap.put(state, paletteMap.size());
                    palette.add(NbtUtils.writeBlockState(state));
                }
            }
            
            blockStatesTag.put("palette", palette);
            
            // Pack block data if more than one block type
            if (paletteMap.size() > 1) {
                int bitsPerBlock = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteMap.size() - 1));
                int blocksPerLong = 64 / bitsPerBlock;
                int dataLength = (BLOCKS_PER_SECTION + blocksPerLong - 1) / blocksPerLong;
                long[] data = new long[dataLength];
                
                for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
                    BlockState state = blocks[i];
                    int paletteId = paletteMap.getOrDefault(state, 0);
                    
                    int longIndex = i / blocksPerLong;
                    int bitOffset = (i % blocksPerLong) * bitsPerBlock;
                    data[longIndex] |= (long) paletteId << bitOffset;
                }
                
                blockStatesTag.putLongArray("data", data);
            }
            
            tag.put("block_states", blockStatesTag);
            
            // Biomes with palette
            CompoundTag biomesTag = new CompoundTag();
            ListTag biomePalette = new ListTag();
            Map<String, Integer> biomePaletteMap = new LinkedHashMap<>();
            
            for (Holder<Biome> biome : biomes) {
                if (biome != null) {
                    String biomeId = biome.unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("minecraft:plains");
                    
                    if (!biomePaletteMap.containsKey(biomeId)) {
                        biomePaletteMap.put(biomeId, biomePaletteMap.size());
                        CompoundTag biomeTag = new CompoundTag();
                        biomeTag.putString("Name", biomeId);
                        biomePalette.add(biomeTag);
                    }
                }
            }
            
            // Default to plains if empty
            if (biomePalette.isEmpty()) {
                CompoundTag defaultBiome = new CompoundTag();
                defaultBiome.putString("Name", "minecraft:plains");
                biomePalette.add(defaultBiome);
            }
            
            biomesTag.put("palette", biomePalette);
            tag.put("biomes", biomesTag);
            
            return tag;
        }
    }
}
