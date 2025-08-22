package com.holybuckets.orecluster;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.client.BalmClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod( Constants.MOD_ID)
public class OreClusterRegenMainForge {

    public OreClusterRegenMainForge() {
        super();
        Balm.initialize(Constants.MOD_ID, CommonClass::init);
        
        // Handle client initialization
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            BalmClient.initialize(Constants.MOD_ID, OreClusterRegenMainClientForge::clientInitializeForge);
        });
    }

}
