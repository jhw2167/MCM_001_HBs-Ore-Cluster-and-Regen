package com.holybuckets.orecluster.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.exception.InvalidId;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.holybuckets.foundation.GeneralConfig.OVERWORLD;


public class OreClusterRegenManager {

    Long periodTickStart;
    Long periodTickEnd;
    Long periodTickLength;
    String periodCurrentStage;

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

        reg.registerOnDailyTick(GeneralConfig.OVERWORLD_LOC, this::onDailyTick);
        LoggerProject.logInit("015000", this.getClass().getName());

    }

    //* BEHAVIOR METHODS *//

    final int TICKS_PER_DAY = (OreClustersAndRegenMain.DEBUG) ? 2400 : (int) GeneralConfig.TICKS_PER_DAY;
    public void setPeriodLength( @Nullable String item) throws InvalidId
    {
        Map<String, Integer> periodLengthByItem = config.getDefaultConfigModel().oreClusterRegenPeriods;
        if( item == null) {
            item = periodLengthByItem.keySet().iterator().next();
        }
        if( !periodLengthByItem.containsKey(item) ) throw new InvalidId("Invalid item for period length: " + item);

        int regenPeriodInDays = periodLengthByItem.get(item);
        this.periodTickLength = (long) regenPeriodInDays*TICKS_PER_DAY;
        this.periodCurrentStage = item;
        updatePeriod(periodTickLength);
    }

    public void updatePeriod(long length) {
        updatePeriod(generalConfig.getTotalTickCountWithSleep(OVERWORLD), length);
    }

    /**
     * Cancels the current period, next period is triggered at start + length
     * @param start The tick count at which the period starts
     * @param length The length of the period in ticks
     *
     */
    public void updatePeriod(long start, long length) {
        this.periodTickLength = length;
        this.periodTickStart = start;
        this.periodTickEnd = start + length;
    }


    private void handleDailyTick(ServerTickEvent.DailyTickEvent event)
    {
        long currentTicks = event.getTickCountWithSleeps();
        if( currentTicks >= periodTickEnd )
        {
            this.triggerRegenThreadExecutor.submit(this::triggerGlobalRegen);
            updatePeriod(currentTicks, periodTickLength);
        }
    }

    //* API
    public int getDaysUntilNewPeriod() {
        long currentTicks = generalConfig.getTotalTickCountWithSleep(OVERWORLD);
        long remainingTicks = periodTickEnd - currentTicks;
        return (int) (remainingTicks / TICKS_PER_DAY);
    }

    public int getDaysIntoPeriod() {
        long currentTicks = generalConfig.getTotalTickCountWithSleep(OVERWORLD);
        long ticksProgressToNextPeriod = currentTicks - periodTickStart;
        return (int) (ticksProgressToNextPeriod / TICKS_PER_DAY);
    }

    public int getDayPeriodLength() {
        return (int) (periodTickLength / TICKS_PER_DAY);
    }

    /**
     * Trigger a global regeneration of all clusters
     */
    public void triggerRegen() {
        this.triggerGlobalRegen();
    }

    /**
     * Trigger regeneration for a specific chunk
     * @param level
     * @param chunkId
     * @throws InvalidId
     */
    public void triggerRegen(LevelAccessor level, String chunkId) throws InvalidId {
        this.triggerChunkRegen(level, chunkId);
    }


    /**
     *  Trigger a global regeneration of all clusters
     */
    private void triggerGlobalRegen() {
        for (OreClusterManager manager : managers.values()) {
            manager.triggerRegen();
        }
    }

    private void triggerChunkRegen(LevelAccessor level, String chunkId) throws InvalidId {
        OreClusterManager manager = managers.get(level);
        if(manager == null) throw new InvalidId("Could not find manager for level" + HBUtil.LevelUtil.toLevelId(level));
        manager.triggerRegen(chunkId, true);
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

        if(!object.has("periodTickStart") || object.get("periodTickStart").isJsonNull()) return false;
        periodTickStart = object.get("periodTickStart").getAsLong();

        if(!object.has("periodTickEnd") || object.get("periodTickEnd").isJsonNull()) return false;
        periodTickEnd = object.get("periodTickEnd").getAsLong();

        if(!object.has("periodTickLength") || object.get("periodTickLength").isJsonNull()) return false;
        periodTickLength = object.get("periodTickLength").getAsLong();

        if(!object.has("periodCurrentStage") || object.get("periodCurrentStage").isJsonNull()) return false;
        periodCurrentStage = object.get("periodCurrentStage").getAsString();

        Map<String, Integer> periods = config.getDefaultConfigModel().oreClusterRegenPeriods;
        if(periods.containsKey(periodCurrentStage)) {       //reflects current configs if changed
            periodTickLength = (long) (periods.get(periodCurrentStage) * TICKS_PER_DAY);
            updatePeriod(periodTickStart, periodTickLength);
        } else {
            LoggerProject.logError("015003", "Invalid periodCurrentStage: " + periodCurrentStage
                + ". Using saved period length of " + periodTickLength);
        }

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
        wrapper.addProperty("periodCurrentStage", periodCurrentStage);


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
    public void onLevelLoad(LevelLoadingEvent.Load event)
    {
        if(event.getLevel().isClientSide()) return;
        if(isLoaded) return;
        this.triggerRegenThreadExecutor = Executors.newSingleThreadExecutor();
        boolean loadedFromFile = load();
        try {
            if(!loadedFromFile) setPeriodLength(null);
        } catch (InvalidId e) {
            LoggerProject.logError("015002", "Error setting period length for OreClusterRegenManager");
            return;
        }

        isLoaded = true;
    }

    public void onLevelUnload(LevelLoadingEvent.Unload event) {
        if(event.getLevel().isClientSide()) return;
        isLoaded = false;
    }


    public void onDailyTick(ServerTickEvent.DailyTickEvent event) {
        this.handleDailyTick(event);
    }


}
