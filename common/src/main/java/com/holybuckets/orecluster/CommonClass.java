package com.holybuckets.orecluster;

import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.orecluster.platform.Services;


public class CommonClass {

    public static OreClustersAndRegenMain mod = null;
    public static boolean isInitialized = false;
    public static void init()
    {
        //Initialize Foundations
        com.holybuckets.foundation.FoundationInitializers.commonInitialize();

        if (Services.PLATFORM.isModLoaded(Constants.MOD_ID)) {
            Constants.LOG.info("Hello to " + Constants.MOD_NAME + "!");
        }
        mod = new OreClustersAndRegenMain();
        BalmEventRegister.registerEvents();
        BalmEventRegister.registerCommands();
        isInitialized = true;
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }

}