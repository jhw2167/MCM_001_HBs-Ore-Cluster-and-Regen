package com.holybuckets.orecluster.mixin;

import com.holybuckets.foundation.util.MixinManager;
import com.holybuckets.foundation.model.ManagedChunkEvents;
import com.holybuckets.orecluster.core.OreClusterBlockStateTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class MixinLevelChunkSection {

    @Shadow
    public abstract BlockState setBlockState(int x, int y, int z, BlockState state, boolean flag);

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
    private void interceptBlockState(int x, int y, int z, BlockState state, boolean flag, CallbackInfoReturnable<BlockState> cir) {
        //LoggerProject.logInfo("099001", "MixinLevelChunkSection setBlock");
        if(MixinManager.isEnabled("MixinLevelChunkSection::setBlockState")) {
            try {
                LevelChunkSection section = (LevelChunkSection) (Object) this;
                OreClusterBlockStateTracker.trackBlockState( section, state, x, y, z);
            }
            catch (Exception e) {
                MixinManager.recordError("MixinLevelChunkSection::setBlockState", e);
            }

        }

    }
}
