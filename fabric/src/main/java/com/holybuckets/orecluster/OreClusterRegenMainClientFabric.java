package com.holybuckets.orecluster;

import com.holybuckets.orecluster.client.CommonClassClient;
import net.fabricmc.api.ClientModInitializer;

public class OreClusterRegenMainClientFabric implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        CommonClassClient.initClient();
    }
}
