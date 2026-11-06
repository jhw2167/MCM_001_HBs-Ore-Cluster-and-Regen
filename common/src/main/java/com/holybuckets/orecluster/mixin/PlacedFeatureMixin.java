package com.holybuckets.orecluster.mixin;

import com.holybuckets.foundation.util.MixinManager;
import com.holybuckets.orecluster.core.OreClusterBlockStateTracker;
import com.holybuckets.orecluster.core.OreFeaturePlacementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Unique
    private static final ThreadLocal<Boolean> replaying = ThreadLocal.withInitial(() -> false);

    @Shadow
    private boolean placeWithContext(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos) {
        throw new AssertionError();
    }

    @Inject(
        method = "placeWithContext(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void scaleOrePlacement(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos, CallbackInfoReturnable<Boolean> cir)
    {
        if (replaying.get()) return;
        if (!MixinManager.isEnabled("PlacedFeatureMixin::scaleOrePlacement")) return;
        try {
            OreFeaturePlacementManager manager = OreFeaturePlacementManager.getManager(placementContext.getLevel().getLevel());
            if (manager == null) return;
            int placements = manager.rollPlacements(blockPos, (PlacedFeature) (Object) this);
            if (placements == 1) return;

            boolean placed = false;
            replaying.set(true);
            try {
                for (int i = 0; i < placements; i++) placed |= placeWithContext(placementContext, randomSource, blockPos);
            } finally {
                replaying.set(false);
            }
            cir.setReturnValue(placed);
        }
        catch (Exception e) {
            MixinManager.recordError("PlacedFeatureMixin::scaleOrePlacement", e);
        }
    }

}
