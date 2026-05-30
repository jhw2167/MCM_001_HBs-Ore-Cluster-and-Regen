package com.holybuckets.foundation;

import com.holybuckets.foundation.capability.FoundationAttachments;
import com.holybuckets.foundation.client.CommonClassClient;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.client.BalmClient;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class FoundationMainForge {

    public FoundationMainForge(IEventBus modEventBus) {
        super();
        // Register NeoForge attachment types to the mod event bus
        final var context = new NeoForgeLoadContext(modEventBus);
        FoundationAttachments.ATTACHMENT_TYPES.register(modEventBus);
        Balm.initialize(Constants.MOD_ID, context, CommonClass::init);
    }

}
