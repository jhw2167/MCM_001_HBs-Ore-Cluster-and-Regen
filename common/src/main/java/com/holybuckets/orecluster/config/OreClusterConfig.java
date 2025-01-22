package com.holybuckets.orecluster.config;

import net.blay09.mods.balm.api.Balm;
import com.holybuckets.orecluster.network.message.SyncOreClusterConfigMessage;

public class OreClusterConfig {

    public static OreClusterConfigData getActive() {
        return Balm.getConfig().getActive(OreClusterConfigData.class);
    }

    public static void initialize() {
        Balm.getConfig().registerConfig(OreClusterConfigData.class, null);
    }
}

