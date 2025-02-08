package com.holybuckets.orecluster.mixin;

import com.holybuckets.orecluster.LoggerProject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Level.class)
public abstract class MixinLevel {

    @Shadow
    public abstract boolean setBlock(BlockPos pos, BlockState state, int i, int flags);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void mapOreClusterBlockState(BlockPos pos, BlockState state, int i, int flags, CallbackInfoReturnable<Boolean> cir) {
        //nothing yet
        //LoggerProject.logInfo("099000", "MixinLevel setBlock");
    }
}
