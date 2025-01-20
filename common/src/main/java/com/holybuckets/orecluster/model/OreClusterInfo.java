package com.holybuckets.orecluster.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

/**
 * Description: Class designed for storing information about ore clusters
 */
public class OreClusterInfo {

    public static final String CLASS_ID = "008";

    public LevelAccessor level;
    public String chunkId;
    public Block oreType;
    public BlockPos position;
    /** Distance from arbitrary point, used in some algorithms **/
    public Double pointDistance;

    public OreClusterInfo(ManagedOreClusterChunk chunk, Block oreType)
    {
        this.level = chunk.getLevel();
        this.chunkId = chunk.getId();
        this.oreType = oreType;
        this.position = chunk.getClusterTypes().get(oreType);
    }
    
    
    public void calcPointDistance(Vec3i point) {
        this.pointDistance = this.position.distSqr(point);
    }
}
