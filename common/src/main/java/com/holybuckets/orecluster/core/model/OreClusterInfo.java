package com.holybuckets.orecluster.core.model;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.OreClusterStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;

public class OreClusterInfo {

    public static final String CLASS_ID = "008";
    public static final int REVEAL_Y_DISTANCE_CHUNKS = 4;

    public LevelAccessor level;
    public String chunkId;
    public ChunkPos chunkPos;
    public OreClusterConfigModel.OreClusterId oreType;
    public BlockPos position;
    public Double pointDistance;
    public OreClusterStatus status;

    public OreClusterInfo(ManagedOreClusterChunk chunk, OreClusterConfigModel.OreClusterId oreType)
    {
        this.level = chunk.getLevel();
        this.chunkId = chunk.getId();
        this.chunkPos = HBUtil.ChunkUtil.getChunkPos(this.chunkId);
        this.oreType = oreType;
        this.status = chunk.getStatus();
        this.position = chunk.getClusterTypes().get(oreType);
    }

    public OreClusterInfo(ChunkPos chunkPos, OreClusterConfigModel.OreClusterId oreType)
    {
        this.chunkId = HBUtil.ChunkUtil.getId(chunkPos);
        this.chunkPos = chunkPos;
        this.oreType = oreType;
    }

    public void calcPointDistance(Vec3i point) {
        BlockPos target = (this.position != null) ? this.position : this.chunkPos.getMiddleBlockPosition(point.getY());
        this.pointDistance = target.distSqr(point);
    }

    public boolean isNear(Vec3i point) {
        if(point == null) return false;
        int dx = (point.getX() >> 4) - this.chunkPos.x;
        int dz = (point.getZ() >> 4) - this.chunkPos.z;
        return dx * dx + dz * dz <= REVEAL_Y_DISTANCE_CHUNKS * REVEAL_Y_DISTANCE_CHUNKS;
    }

    private String getOreName() {
        String ore = HBUtil.BlockUtil.blockToString(this.oreType.getBlock());
        return ore.substring(ore.lastIndexOf(":") + 1);
    }

    @Override
    public String toString() {
        return getOreName() + HBUtil.ChunkUtil.getId(chunkPos);
    }

    public String toString(Vec3i viewer) {
        if(this.position == null || !isNear(viewer)) return toString();
        return getOreName() + " at " + HBUtil.BlockUtil.positionToString(this.position);
    }
}
