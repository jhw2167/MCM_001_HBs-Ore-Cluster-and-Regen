package com.holybuckets.orecluster.core;

import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;

/**
 * Description: Tracks BlockState updates to a chunk before the chunk is full
 *  First collects all relevant ores placed in a chunk,
 * then attempts
 */
public class OreClusterBlockStateTracker
{
    static ChunkAccess currentChunk;
    static LevelAccessor currentLevel;
    static ManagedOreClusterChunk currentManagedOreClusterChunk;
    static Map<Block, OreClusterConfigModel> trackingOres;

    public static void setTrackingChunk(ServerLevel level, ChunkAccess chunk) {
        currentChunk = chunk;
        currentLevel = level;
        OreClusterManager manager = OreClusterManager.getManager(level);
        currentManagedOreClusterChunk = manager.getDeterminedOreClusterChunk(chunk);
        trackingOres = manager.getConfig().getOreConfigs();
    }

    public static void trackBlockState(BlockState state,  int x, int y, int z) {
        if( currentManagedOreClusterChunk == null ) return;
        currentManagedOreClusterChunk.addOre(state, x, y, z);
    }


}
