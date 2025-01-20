package com.holybuckets.orecluster.event;

import com.holybuckets.orecluster.OreClustersAndRegenMain;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.FORGE, modid = OreClustersAndRegenMain.MODID)
public class OreClusterForgeEventHandler {

    //create class_id
    public static final String CLASS_ID = "006";


}
