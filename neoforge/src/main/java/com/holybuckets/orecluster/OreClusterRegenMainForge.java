package com.holybuckets.orecluster;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class OreClusterRegenMainForge {

    public OreClusterRegenMainForge(IEventBus modEventBus) {
        Balm.initialize(Constants.MOD_ID, new NeoForgeLoadContext(modEventBus), CommonClass::init);
    }

}
