package com.holybuckets.orecluster.mixin;

import com.holybuckets.foundation.util.MixinManager;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.core.OreClusterBlockStateTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {


    @Inject(
        method = "placeWithContext(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD")
    )
    private void onPlaceWithContext(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos, CallbackInfoReturnable ci)
    {
        // Custom logic before placeWithContext runs
        if( placementContext.getLevel().isClientSide() ) {
        }
        else
        {
            if(MixinManager.isEnabled("PlacedFeatureMixin::onPlaceWithContext")) {
                try {
                    PlacedFeature f = placementContext.topFeature().orElse(null);
                    if(f == null ) return;

                    ChunkAccess c = placementContext.getLevel().getChunk(blockPos);
                    ServerLevel l = placementContext.getLevel().getLevel();
                    OreClusterBlockStateTracker.setTrackingChunk( l, c, blockPos );    }
                catch (Exception e) {
                    MixinManager.recordError("PlacedFeatureMixin::onPlaceWithContext", e);
                }

            }

        }
    }

}
