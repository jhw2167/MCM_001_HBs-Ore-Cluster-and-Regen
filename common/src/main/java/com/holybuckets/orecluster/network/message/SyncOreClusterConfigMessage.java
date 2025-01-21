package com.holybuckets.orecluster.network.message;

import com.holybuckets.orecluster.config.OreClusterConfigData;
import net.blay09.mods.balm.api.network.SyncConfigMessage;

public class SyncOreClusterConfigMessage extends SyncConfigMessage<OreClusterConfigData> {
    public SyncOreClusterConfigMessage(OreClusterConfigData data) { super(data); }
}
