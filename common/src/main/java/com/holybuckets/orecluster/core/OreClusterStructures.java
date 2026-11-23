package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.structure.StructureAPI;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

public class OreClusterStructures {

    public static ResourceLocation toStructureType(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        String prefix = key.getNamespace().equals("minecraft") ? "" : key.getNamespace() + "_";
        return new ResourceLocation(Constants.MOD_ID, prefix + key.getPath() + "_cluster");
    }

    public static void addClusterStructures(Level level, ManagedOreClusterChunk chunk) {
        if(level == null || chunk == null || !chunk.hasClusters()) return;
        Map<OreClusterId, BlockPos> clusters = new HashMap<>(chunk.getClusterTypes());
        clusters.forEach((oreType, pos) -> {
            if(oreType == null || pos == null) return;
            StructureAPI.addPseudoStructure(level, toStructureType(oreType.getBlock()), pos);
        });
    }

}
