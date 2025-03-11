package com.holybuckets.orecluster.core.model;

import com.holybuckets.orecluster.core.OreClusterStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Description: Class designed for storing information about ore clusters
 */
public class OreClusterInfo {

    public static final String CLASS_ID = "008";

    public LevelAccessor level;
    public String chunkId;
    public BlockState oreType;
    public BlockPos position;
    /** Distance from arbitrary point, used in some algorithms **/
    public Double pointDistance;
    public OreClusterStatus status;

    public OreClusterInfo(ManagedOreClusterChunk chunk, BlockState oreType)
    {
        this.level = chunk.getLevel();
        this.chunkId = chunk.getId();
        this.oreType = oreType;
        this.status = chunk.getStatus();
        this.position = chunk.getClusterTypes().get(oreType);
    }
    
    
    public void calcPointDistance(Vec3i point) {
        this.pointDistance = this.position.distSqr(point);
    }
}
