package com.holybuckets.orecluster.event;

import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.config.AllConfigs;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = OreClustersAndRegenMain.MODID)
public class OreClusterModEventHandler {


    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event)
    {

        AllConfigs.onLoad( event );
        OreClustersAndRegenMain.onLoad( event );

        LoggerProject.logInit( "006002","Handler-onLoad" );
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event)
    {
        AllConfigs.onReload( event );
        OreClustersAndRegenMain.onReload( event );

        LoggerProject.logInit( "006003", "Handler-onReLoad" );
    }




}
