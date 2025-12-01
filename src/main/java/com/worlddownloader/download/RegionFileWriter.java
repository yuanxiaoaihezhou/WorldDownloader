package com.worlddownloader.download;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.io.*;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes chunk data to Minecraft Anvil region files (.mca format).
 * Region files contain 32x32 chunks and use a header for indexing.
 */
public class RegionFileWriter implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int SECTOR_SIZE = 4096;
    private static final int HEADER_SIZE = SECTOR_SIZE * 2; // Offset table + Timestamp table
    private static final int CHUNKS_PER_REGION = 32;
    
    private final File file;
    private final RandomAccessFile raf;
    private final int[] offsets = new int[1024]; // 32x32 chunks
    private final int[] timestamps = new int[1024];
    private int nextSector = 2; // Start after header
    
    public RegionFileWriter(File file) throws IOException {
        this.file = file;
        
        // Create or open file
        boolean exists = file.exists();
        this.raf = new RandomAccessFile(file, "rw");
        
        if (exists && raf.length() >= HEADER_SIZE) {
            // Read existing header
            raf.seek(0);
            for (int i = 0; i < 1024; i++) {
                offsets[i] = raf.readInt();
            }
            for (int i = 0; i < 1024; i++) {
                timestamps[i] = raf.readInt();
            }
            
            // Find next available sector
            for (int offset : offsets) {
                if (offset != 0) {
                    int sectorOffset = offset >> 8;
                    int sectorCount = offset & 0xFF;
                    nextSector = Math.max(nextSector, sectorOffset + sectorCount);
                }
            }
        } else {
            // Initialize empty header
            raf.setLength(HEADER_SIZE);
            raf.seek(0);
            for (int i = 0; i < 2048; i++) {
                raf.writeInt(0);
            }
        }
    }
    
    public void writeChunk(ChunkPos pos, CompoundTag chunkData) throws IOException {
        int localX = pos.x & (CHUNKS_PER_REGION - 1);
        int localZ = pos.z & (CHUNKS_PER_REGION - 1);
        int index = localX + localZ * CHUNKS_PER_REGION;
        
        // Serialize chunk data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(new DeflaterOutputStream(baos))) {
            NbtIo.write(chunkData, dos);
        }
        
        byte[] compressedData = baos.toByteArray();
        int dataLength = compressedData.length + 5; // 4 bytes length + 1 byte compression type
        int sectorsNeeded = (dataLength + SECTOR_SIZE - 1) / SECTOR_SIZE;
        
        // Find or allocate sectors
        int sectorOffset = nextSector;
        nextSector += sectorsNeeded;
        
        // Write chunk data
        raf.seek((long) sectorOffset * SECTOR_SIZE);
        raf.writeInt(compressedData.length + 1); // Length includes compression byte
        raf.writeByte(2); // Compression type: 2 = zlib
        raf.write(compressedData);
        
        // Pad to sector boundary
        int padding = (sectorsNeeded * SECTOR_SIZE) - dataLength;
        if (padding > 0) {
            raf.write(new byte[padding]);
        }
        
        // Update offset table
        offsets[index] = (sectorOffset << 8) | (sectorsNeeded & 0xFF);
        timestamps[index] = (int) (System.currentTimeMillis() / 1000);
        
        // Write updated header entry
        raf.seek(index * 4L);
        raf.writeInt(offsets[index]);
        raf.seek(SECTOR_SIZE + index * 4L);
        raf.writeInt(timestamps[index]);
    }
    
    @Override
    public void close() throws IOException {
        raf.close();
    }
}
