package com.holybuckets.orecluster.network;

import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import com.holybuckets.orecluster.network.message.SyncOreClusterConfigMessage;
import net.blay09.mods.balm.api.network.BalmNetworking;
import net.blay09.mods.balm.api.network.SyncConfigMessage;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ModNetworking {

    public static void initialize(BalmNetworking networking)
    {
        //networking.registerServerboundPacket(id("inventory_button"), InventoryButtonMessage.class, InventoryButtonMessage::encode, InventoryButtonMessage::decode, InventoryButtonMessage::handle);
        //networking.registerClientboundPacket(id("waystone_update"), UpdateWaystoneMessage.class, UpdateWaystoneMessage::encode, UpdateWaystoneMessage::decode, UpdateWaystoneMessage::handle);

        SyncConfigMessage.register(id("sync_config"), SyncOreClusterConfigMessage.class, SyncOreClusterConfigMessage::new, OreClusterConfigData.class, OreClusterConfigData::new);
    }

    @NotNull
    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }

}

