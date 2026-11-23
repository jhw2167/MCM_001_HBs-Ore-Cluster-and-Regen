package com.holybuckets.orecluster.mixin;

import com.holybuckets.foundation.util.MixinManager;
import com.holybuckets.orecluster.core.OreFeaturePlacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredFeature.class)
public class ConfiguredFeatureMixin {

    @Inject(
        method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void rejectAboveYLevel(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir)
    {
        if (!MixinManager.isEnabled("ConfiguredFeatureMixin::rejectAboveYLevel")) return;
        try {
            ConfiguredFeature<?, ?> feature = (ConfiguredFeature<?, ?>) (Object) this;
            if (!(feature.config() instanceof OreConfiguration)) return;
            if (OreFeaturePlacementManager.rejectAboveYLevel(origin, random)) cir.setReturnValue(false);
        }
        catch (Exception e) {
            MixinManager.recordError("ConfiguredFeatureMixin::rejectAboveYLevel", e);
        }
    }

}
