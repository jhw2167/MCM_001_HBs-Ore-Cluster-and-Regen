package com.holybuckets.orecluster.mixin;

import com.holybuckets.foundation.util.MixinManager;
import com.holybuckets.orecluster.core.OreClusterBlockStateTracker;
import com.holybuckets.orecluster.core.OreFeaturePlacementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

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
                    OreClusterBlockStateTracker.beginTracking( l, c );    }
                catch (Exception e) {
                    MixinManager.recordError("PlacedFeatureMixin::onPlaceWithContext", e);
                }

            }

        }
    }

    @Inject(
        method = "placeWithContext(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("RETURN")
    )
    private void afterPlaceWithContext(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos, CallbackInfoReturnable ci)
    {
        if( placementContext.getLevel().isClientSide() ) return;
        if( !MixinManager.isEnabled("PlacedFeatureMixin::onPlaceWithContext") ) return;
        if( placementContext.topFeature().isEmpty() ) return;
        try {
            OreClusterBlockStateTracker.endTracking();
        }
        catch (Exception e) {
            MixinManager.recordError("PlacedFeatureMixin::onPlaceWithContext", e);
        }
    }

    @Shadow
    private boolean placeWithContext(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos) {
        throw new AssertionError();
    }

    @Inject(
        method = "placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void scaleOrePlacement(WorldGenLevel level, ChunkGenerator generator, RandomSource randomSource, BlockPos blockPos, CallbackInfoReturnable<Boolean> cir)
    {
        if (!MixinManager.isEnabled("PlacedFeatureMixin::scaleOrePlacement")) return;
        try {
            OreFeaturePlacementManager manager = OreFeaturePlacementManager.getManager(level.getLevel());
            PlacedFeature feature = (PlacedFeature) (Object) this;
            int placements = manager.rollPlacements(blockPos, feature);
            if (placements == 1) return;

            PlacementContext context = new PlacementContext(level, generator, Optional.of(feature));
            boolean placed = false;
            for (int i = 0; i < placements; i++)
                placed |= placeWithContext(context, randomSource, blockPos);
            cir.setReturnValue(placed);
        }
        catch (Exception e) {
            MixinManager.recordError("PlacedFeatureMixin::scaleOrePlacement", e);
        }
    }

}
