package com.holybuckets.orecluster;

import com.holybuckets.orecluster.client.CommonClassClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class OreClusterRegenMainClientForge {

    public static void clientInitializeForge() {
        CommonClassClient.initClient();
    }
}
