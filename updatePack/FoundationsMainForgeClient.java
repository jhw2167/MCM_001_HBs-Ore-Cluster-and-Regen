package com.holybuckets.foundation.client;

import com.holybuckets.foundation.Constants;
import net.blay09.mods.balm.api.client.BalmClient;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class FoundationsMainForgeClient {

    public FoundationsMainForgeClient(IEventBus modEventBus) {
        final var context = new NeoForgeLoadContext(modEventBus);
        BalmClient.initialize(Constants.MOD_ID, context, CommonClassClient::initClient);
    }
}
