package com.holybuckets.orecluster.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.LevelSaveData;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import net.blay09.mods.balm.api.event.BalmEvent;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.minecraft.world.level.LevelAccessor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class OreClusterRegenManager {

    Long periodTickStart;
    Long periodTickEnd;
    Long periodTickLength;

    Map<LevelAccessor, OreClusterManager> managers;

    GeneralConfig generalConfig;
    ModRealTimeConfig config;

    private static boolean isLoaded = false;

    public OreClusterRegenManager(EventRegistrar reg, ModRealTimeConfig config, Map<LevelAccessor, OreClusterManager> managers)
    {
        super();
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
    }

    //Behaviour methods

    final int TICKS_PER_DAY = 2400;
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

            updatePeriod(periodTickLength);
        }
    }


    /**
     *
     */
    private void triggerRegenThread() {

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


    //* EVENTS
    public void onLevelLoad(LevelLoadingEvent.Load event) {
        if(isLoaded) return;
        boolean loadedFromFile = load();
        if(!loadedFromFile) setPeriodLength(null);
        isLoaded = true;
    }

    public void onLevelUnload(LevelLoadingEvent.Unload event) {
        save(DatastoreSaveEvent.create());
        isLoaded = false;
    }


    public void onDailyTick(ServerTickEvent event) {
        this.handleDailyTick(event.getTickCount());
    }


}
