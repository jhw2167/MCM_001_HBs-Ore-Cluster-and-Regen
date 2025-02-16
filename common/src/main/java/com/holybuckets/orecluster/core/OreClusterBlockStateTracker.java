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

import java.util.Arrays;
import java.util.Map;

/**
 * Description: Tracks BlockState updates to a chunk before the chunk is full
 *  First collects all relevant ores placed in a chunk,
 * then attempts
 */
public class OreClusterBlockStateTracker
{
    static ChunkAccess currentChunk;
    static String chunkId;
    static LevelAccessor currentLevel;
    static ManagedOreClusterChunk currentManagedOreClusterChunk;
    static Map<BlockState, OreClusterConfigModel> trackingOreConfig;

    public static void setTrackingChunk(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        currentChunk = chunk;
        currentLevel = level;
        OreClusterManager manager = OreClusterManager.getManager(level);
        chunkId = HBUtil.ChunkUtil.getId(currentChunk);
        currentManagedOreClusterChunk = manager.getDeterminedOreClusterChunk( chunkId );
        if( trackingOreConfig == null ) {
            Map<Block, OreClusterConfigModel> map = manager.getConfig().getOreConfigs();
            trackingOreConfig = map.entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                            e -> e.getKey().defaultBlockState(),
                            e -> e.getValue()
                    )
            );
        }

    }

    //This will be called before features start being placed, (before there is a chunk to track)

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

    //This will be called before features start being placed, (before there is a chunk to track)
    public static void trackBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( trackingOreConfig == null ) return;
        if( currentManagedOreClusterChunk == null ) return;
        ManagedOreClusterChunk chunk = currentManagedOreClusterChunk;
        if( !trackingOreConfig.containsKey(state) ) return;

        if( !chunk.sampleAddOre(state) ) return;

        HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
        int sectionNum = Arrays.stream(currentChunk.getSections()).toList().indexOf(section);
        HBUtil.WorldPos pos = new HBUtil.WorldPos( relativePos, sectionNum, currentChunk );

        if(pos.getWorldPos().getY() > trackingOreConfig.get(state).oreClusterMaxYLevelSpawn)
            return;

        chunk.addOre(state, pos.getWorldPos(), true);
    }


}
