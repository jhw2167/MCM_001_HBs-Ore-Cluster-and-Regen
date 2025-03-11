package com.holybuckets.orecluster.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.orecluster.LoggerProject;
import net.minecraft.world.level.LevelAccessor;

import java.util.Map;

import static com.holybuckets.orecluster.OreClustersAndRegenMain.DEBUG;

public class OreClusterHealthCheck {
    private static OreClusterHealthCheck INSTANCE;
    private final Gson gson;
    private OreClusterInterface oreClusterInterface;
    private final Map<LevelAccessor, OreClusterManager> managers;

    //Threads
    private Thread statisticHealthCheckThread;

    private OreClusterHealthCheck(EventRegistrar reg, OreClusterInterface interfacer, Map<LevelAccessor, OreClusterManager> managers) {
        this.oreClusterInterface = interfacer;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.managers = managers;

        if (DEBUG) {
            reg.registerOnServerTick(EventRegistrar.TickType.ON_1200_TICKS, this::onDailyTick);
        } else {
            reg.registerOnServerTick(EventRegistrar.TickType.DAILY_TICK, this::onDailyTick);
        }

    }

    public static OreClusterHealthCheck initInstance(EventRegistrar reg, OreClusterInterface interfacer, Map<LevelAccessor, OreClusterManager> managers) {
        INSTANCE = new OreClusterHealthCheck(reg, interfacer, managers);
        return INSTANCE;
    }

    //* BEHAVIOR

    /**
     * Logs the size of all queues for each level manager, average processing time
     */
    private void statisticHealthCheck()
    {
        try {
            for (OreClusterManager m : managers.values()) {
                JsonElement jsonHealthCheck = oreClusterInterface.healthCheck(m);
                StringBuilder message = new StringBuilder("Manager Health Check for level: ");
                message.append(HBUtil.LevelUtil.toLevelId(m.getLevel()));
                message.append("\n\n");
                message.append(gson.toJson(jsonHealthCheck));
                LoggerProject.logInfo("001001", message.toString());
            }
        } catch (Exception e) {
            LoggerProject.logWarning("001002", "Manager Health Check Thread Exception: " + e.getMessage());
        }
        finally {
            this.statisticHealthCheckThread = null;
        }
    }

    private void chunkLoadsHealthCheck()
    {

    }





    //* EVENTS
    private void onDailyTick(ServerTickEvent event) {
        if (this.statisticHealthCheckThread == null) {
            this.statisticHealthCheckThread = new Thread(this::statisticHealthCheck, "OreClusterAndRegenMain-ManagerHealthCheck");
            this.statisticHealthCheckThread.start();
        }
    }



}
//END CLASS
