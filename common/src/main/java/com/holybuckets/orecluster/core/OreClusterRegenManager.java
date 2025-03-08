package com.holybuckets.orecluster.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.minecraft.world.level.LevelAccessor;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class OreClusterRegenManager {

    Long periodTickStart;
    Long periodTickEnd;
    Long periodTickLength;

    ExecutorService triggerRegenThreadExecutor;
    Map<LevelAccessor, OreClusterManager> managers;

    GeneralConfig generalConfig;
    ModRealTimeConfig config;

    private static boolean isLoaded = false;

    public OreClusterRegenManager(EventRegistrar reg, ModRealTimeConfig config, Map<LevelAccessor, OreClusterManager> managers)
    {
        super();
        this.triggerRegenThreadExecutor = Executors.newSingleThreadExecutor();
        this.managers = managers;
        this.config = config;
        this.generalConfig = GeneralConfig.getInstance();
        this.init(reg);
    }


    public void init(EventRegistrar reg) {
        //Register
        reg.registerOnLevelLoad(this::onLevelLoad, EventPriority.Normal);
        reg.registerOnLevelUnload(this::onLevelUnload, EventPriority.Normal);
        reg.registerOnDataSave(this::save, EventPriority.High);

        if( OreClustersAndRegenMain.DEBUG ) {
            reg.registerOnServerTick(EventRegistrar.TickType.ON_120_TICKS, this::onDailyTick);
        } else {
            reg.registerOnServerTick(EventRegistrar.TickType.DAILY_TICK, this::onDailyTick);
        }
    }

    //* BEHAVIOR METHODS *//

    final int TICKS_PER_DAY = (OreClustersAndRegenMain.DEBUG) ? 240 : 24000;
    public void setPeriodLength(String item)
    {
        Map<String, Integer> periodLengthByItem = config.getDefaultConfig().oreClusterRegenPeriods;
        if( item == null) {
            item = periodLengthByItem.keySet().iterator().next();
        }
        int regenPeriodInDays = periodLengthByItem.get(item);
        this.periodTickLength = Long.valueOf( regenPeriodInDays*TICKS_PER_DAY );
        updatePeriod(periodTickLength);
    }

    private void updatePeriod(long length) {
        this.periodTickStart = generalConfig.getTotalTickCount();
        this.periodTickEnd = periodTickStart + periodTickLength;
    }

    private void handleDailyTick(long tickCount) {
        if( tickCount > periodTickEnd )
        {
            this.triggerRegenThreadExecutor.submit(this::triggerRegenThread);
            updatePeriod(periodTickLength);
        }
    }


    /**
     *
     */
    private void triggerRegenThread() {
        for (OreClusterManager manager : managers.values()) {
            manager.triggerRegen();
        }
    }


    //* LOAD AND UNLOAD UTILITY FUNCTIONS

    public boolean load()
    {
        //Load Mod Datastore, if one does not exist for this mod,
        //read determinedSourceChunks into an array and save it to levelSavedata
        DataStore ds = GeneralConfig.getInstance().getDataStore();
        if (ds == null) return false;

        WorldSaveData worldSaveData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        JsonElement wrapper = worldSaveData.get("oreClusterRegenManager");
        if(wrapper == null || wrapper.isJsonNull() ) return false;

        JsonObject object = wrapper.getAsJsonObject();

        periodTickStart = object.get("periodTickStart").getAsLong();
        periodTickEnd = object.get("periodTickEnd").getAsLong();
        periodTickLength = object.get("periodTickLength").getAsLong();

        return true;
    }

    public void save(DatastoreSaveEvent event) {
        //Create new Mod Datastore, if one does not exist for this mod,
        //read determinedSourceChunks into an array and save it to levelSavedata
        DataStore ds = event.getDataStore();
        if (ds == null) return;

        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("periodTickStart", periodTickStart);
        wrapper.addProperty("periodTickEnd", periodTickEnd);
        wrapper.addProperty("periodTickLength", periodTickLength);


        WorldSaveData worldSaveData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        worldSaveData.addProperty("oreClusterRegenManager", wrapper);
    }

    public void shutdown() {
        try {
            triggerRegenThreadExecutor.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            triggerRegenThreadExecutor.shutdownNow();
        } catch (InterruptedException e) {
            LoggerProject.logWarning("015001", "Error shutting down OreClusterRegenManager, regen thread was in progress");
        }
        save(DatastoreSaveEvent.create());
    }


    //* EVENTS
    public void onLevelLoad(LevelLoadingEvent.Load event) {
        if(isLoaded) return;
        boolean loadedFromFile = load();
        if(!loadedFromFile) setPeriodLength(null);
        isLoaded = true;
    }

    public void onLevelUnload(LevelLoadingEvent.Unload event) {
        isLoaded = false;
    }


    public void onDailyTick(ServerTickEvent event) {
        this.handleDailyTick(event.getTickCount());
        LoggerProject.logInfo("015001", "Ore Cluster Regen Manager: TICKS: " + event.getTickCount() + " END " + periodTickEnd);
    }


}
