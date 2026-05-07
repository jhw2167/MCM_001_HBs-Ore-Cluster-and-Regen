package com.holybuckets.orecluster;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.BalmRuntimeLoadContext;
import net.blay09.mods.balm.api.EmptyLoadContext;
import net.fabricmc.api.ModInitializer;

public class OreClusterRegenMainFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        Balm.initialize(Constants.MOD_ID, EmptyLoadContext.INSTANCE, CommonClass::init);
    }
}
