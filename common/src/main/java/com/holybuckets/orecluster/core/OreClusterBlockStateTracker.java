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
import net.minecraft.world.level.chunk.LevelChunk;
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
    static Map<BlockState, OreClusterConfigModel> trackingOres;

    public static void setTrackingChunk(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        currentChunk = chunk;
        currentLevel = level;
        OreClusterManager manager = OreClusterManager.getManager(level);
        chunkId = HBUtil.ChunkUtil.getId(currentChunk);
        currentManagedOreClusterChunk = manager.getDeterminedOreClusterChunk( chunkId );
        if( trackingOres == null ) {
            Map<Block, OreClusterConfigModel> map = manager.getConfig().getOreConfigs();
            trackingOres = map.entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                            e -> e.getKey().defaultBlockState(),
                            e -> e.getValue()
                    )
            );
        }

    }

    //This will be called before features start being placed, (before there is a chunk to track)
    public static void trackBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( currentManagedOreClusterChunk == null ) return;
        ManagedOreClusterChunk chunk = currentManagedOreClusterChunk;
        if( !trackingOres.containsKey(state) ) return;

        HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
        int sectionNum = Arrays.stream(currentChunk.getSections()).toList().indexOf(section);
        HBUtil.WorldPos pos = new HBUtil.WorldPos( relativePos, sectionNum, currentChunk );
        chunk.addOre(state, pos.getWorldPos());
    }


}
