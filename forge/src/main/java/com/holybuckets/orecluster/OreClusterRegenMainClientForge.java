package com.holybuckets.orecluster;

import com.holybuckets.orecluster.client.CommonClassClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public class OreClusterRegenMainClientForge {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CommonClassClient.initClient();
    }
}
