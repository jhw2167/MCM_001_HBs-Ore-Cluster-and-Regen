package com.holybuckets.orecluster;

import net.blay09.mods.balm.api.Balm;
import net.fabricmc.api.ModInitializer;

public class OreClusterRegenMainFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        Balm.initialize(Constants.MOD_ID, CommonClass::init);
    }
}
