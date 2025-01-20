package com.holybuckets.orecluster;

import com.holybuckets.orecluster.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;


public class CommonClass {

    public static OreClustersAndRegenMain mod = null;
    public static void init()
    {

        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        Constants.LOG.info("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        if (Services.PLATFORM.isModLoaded(Constants.MOD_ID)) {
            Constants.LOG.info("Hello to " + Constants.MOD_NAME + "!");
        }

        mod = new OreClustersAndRegenMain();

    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }

}