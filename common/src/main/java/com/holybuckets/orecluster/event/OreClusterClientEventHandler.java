package com.holybuckets.orecluster.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

//Project Imports
import com.holybuckets.orecluster.OreClustersAndRegenMain;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.FORGE, modid = OreClustersAndRegenMain.MODID, value = Dist.CLIENT)
public class OreClusterClientEventHandler {

    public OreClusterClientEventHandler() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // Client setup code here
    }


}
