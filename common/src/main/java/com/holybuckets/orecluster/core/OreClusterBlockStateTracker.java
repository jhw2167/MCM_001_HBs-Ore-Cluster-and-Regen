package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description: Tracks BlockState updates to a chunk before the chunk is full
 *  First collects all relevant ores placed in a chunk,
 * then attempts
 */
public class OreClusterBlockStateTracker
{
    static ChunkAccess currentChunk;
    static Map<LevelChunkSection, Integer> chunkSections;
    static String chunkId;
    static LevelAccessor currentLevel;
    static ManagedOreClusterChunk currentManagedOreClusterChunk;
    static Map<BlockState, OreClusterConfigModel> trackingOreConfig;

    private static int chunkCount = 0;
    public static void setTrackingChunk(ServerLevel level, ChunkAccess chunk, BlockPos pos)
    {
        chunkCount++;
        currentChunk = chunk;
        currentLevel = level;
        chunkSections = new ConcurrentHashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        Arrays.stream(chunk.getSections()).forEach((section) -> {
            chunkSections.put(section, count.getAndIncrement());
        });
        OreClusterManager manager = OreClusterManager.getManager(level);
        if( manager == null ) {
            return;
        }
        chunkId = HBUtil.ChunkUtil.getId(currentChunk);
        currentManagedOreClusterChunk = manager.getDeterminedOreClusterChunk( chunkId );
        if( trackingOreConfig == null )
            trackingOreConfig = manager.getConfig().getOreConfigs();


    }


    //This will be called before features start being placed, (before there is a chunk to track)
    private static long blockCount =0;
    public static void trackBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( trackingOreConfig == null ) return;
        if( currentManagedOreClusterChunk == null ) return;
        ManagedOreClusterChunk chunk = currentManagedOreClusterChunk;
        if( !trackingOreConfig.containsKey(state) ) return;
        OreClusterConfigModel config = trackingOreConfig.get(state);
        LevelAccessor oreLevel = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, config.oreClusterDimensionId);
         if( currentLevel != oreLevel ) return;


        blockCount++;
        int secIndex = -1;
        if( chunkSections.containsKey(section) ) {
            secIndex = chunkSections.get(section);
        }
        if( !chunk.sampleAddOre(state, secIndex) ) return;

        HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
        HBUtil.WorldPos pos = new HBUtil.WorldPos( relativePos, secIndex, currentChunk );
        chunk.addOre(state, pos.getWorldPos(), true);
    }


    /**
     *
     * @param section
     * @param state
     * @param x
     * @param y
     * @param z
     */
    public static void mapBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( trackingOreConfig == null ) return;
        if( currentManagedOreClusterChunk == null ) return;
        ManagedOreClusterChunk chunk = currentManagedOreClusterChunk;
        if( !trackingOreConfig.containsKey(state) ) return;

        state = chunk.mapBlockState(state);
    }



}
