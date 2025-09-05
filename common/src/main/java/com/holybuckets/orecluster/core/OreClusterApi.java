package com.holybuckets.orecluster.core;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.exception.InvalidId;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import com.holybuckets.orecluster.core.model.OreClusterInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

/**
 * Description: Class designed for interfacing with OreClusterManager and perform lookup operations such as:
 *      - Locate Ore Clusters
 *      - Locate Ore
 *      - Count Ore Clusters
 */
public class OreClusterApi {

    public static final String CLASS_ID = "008";
    private static OreClusterApi INSTANCE;

    private final ModRealTimeConfig modConfig;
    private final Map<LevelAccessor, OreClusterManager> managers;
    private final OreClusterRegenManager regenManager;

    public static boolean isInit() {
        return INSTANCE != null;
    }

    /**
     * Gets instance of oreClusterApi, returns null if instance is not initialized
     * @return
     */
    public static OreClusterApi getInstance() {
        if(INSTANCE == null)
            return null;
        return INSTANCE;
    }

    public OreClusterApi(Map<LevelAccessor, OreClusterManager> managers, ModRealTimeConfig modConfig, OreClusterRegenManager regenManager) {
        this.modConfig = modConfig;
        this.managers = managers;
        this.regenManager = regenManager;
        INSTANCE = this;
    }


    public JsonObject getConfigSummary()
    {
        JsonObject resp = new JsonObject();
        //Return default config
        OreClusterConfigModel config = modConfig.getDefaultConfig();
        JsonArray allOresArray = new JsonArray();

        //sort oreconfigs by configId 0000 to 9999
        List<OreClusterConfigModel> ores = modConfig.getOreConfigs().values().stream()
            .sorted(Comparator.comparingInt(a -> Integer.parseInt(a.configId)))
            .toList();
        for(OreClusterConfigModel oreConfig : ores )
        {
            JsonObject oreObj = new JsonObject();
            oreObj.addProperty("header", "Ore With ConfigId: " + oreConfig.configId + ":");
            oreObj.addProperty("dimension", oreConfig.oreClusterDimensionId);
            oreObj.addProperty("clusterType", HBUtil.BlockUtil.blockToString(oreConfig.oreClusterType.getBlock()) );
            oreObj.addProperty("clusterSpawnRate", oreConfig.oreClusterSpawnRate);
            oreObj.addProperty("clusterRegenerates", (oreConfig.oreClusterDoesRegenerate) ? "yes" : "no");
            //oreObj.addProperty("vanillaSuperVeinsEnabled", config.oreClusterSpawnRate);
            //oreObj.addProperty("biome", config.oreClusterSpawnRate);
            allOresArray.add(oreObj);
        }

        //Summary stats
        JsonObject oreObj = new JsonObject();
        oreObj.addProperty("header", "Summary stats:");
        oreObj.addProperty("randomSubseed", config.subSeed);
        oreObj.addProperty("totalConfigs", modConfig.getOreConfigs().size());
        oreObj.addProperty("periodRegenLengths", config.oreClusterRegenPeriods.entrySet()
            .stream()
            .map( e -> e.getKey() + ":" + e.getValue() + " days" )
            .toList().toString());
        int daysIn = regenManager.getDaysIntoPeriod();
        int totalDays = regenManager.getDayPeriodLength();
        oreObj.addProperty("currentPeriod", daysIn + " of " + totalDays + " days");

        allOresArray.add(oreObj);

        //Create a json array of all the level ids in the game called "Active Dimension Ids:"
        JsonObject levelIds = new JsonObject();
        levelIds.addProperty("header", "Active Dimension Ids:");
        List<String> levelids = managers.keySet().stream()
            .map( HBUtil.LevelUtil::toLevelId )
            .map( id -> id.replaceAll("CLIENT:","").replaceAll("SERVER:","") )
            .toList();
            int i = 0;
        for(String id : levelids) {
            levelIds.addProperty(""+i++, id);
        }


        allOresArray.add(levelIds);

        resp.addProperty("header", "Configured ores:\n");
        resp.add("value", allOresArray);

        return resp;
    }

    public JsonObject getConfig(String configId)
    {
        if(configId == null) return getConfigSummary();
        OreClusterConfigModel targetConfig = modConfig.getOreConfig( Integer.parseInt(configId) );
        if(targetConfig == null) return null;

        JsonObject resp = new JsonObject();
        resp.addProperty("header", "Config summary for cluster config id: " + configId);
        
        JsonArray configArray = new JsonArray();
        JsonObject configObj = new JsonObject();
        
        // Add ore type first
        configObj.addProperty("header", "");
        configObj.addProperty("oreClusterType",
        HBUtil.BlockUtil.blockToString(targetConfig.oreClusterType.getBlock()) );
        
        // Add all other config properties
        configObj.addProperty("configId", targetConfig.configId);
        configObj.addProperty("dimensionId", targetConfig.oreClusterDimensionId);
        configObj.addProperty("spawnRate", targetConfig.oreClusterSpawnRate);
        configObj.addProperty("size", targetConfig.oreClusterVolume.toString());
        configObj.addProperty("density", targetConfig.oreClusterDensity.toString());
        configObj.addProperty("shape", targetConfig.oreClusterShape);
        configObj.addProperty("oreClusterDoesRegenerate", targetConfig.oreClusterDoesRegenerate);
        configObj.addProperty("maxYLevelSpawnAllowed", targetConfig.oreClusterMaxYLevelSpawn);
        configObj.addProperty("minYLevelSpawnAllowed", targetConfig.oreClusterMinYLevelSpawn);
        configObj.addProperty("nonReplaceableBlocks", targetConfig.oreClusterNonReplaceableBlocks.toString());
        configObj.addProperty("alternativeClusterBlocks", targetConfig.oreClusterReplaceableEmptyBlocks.toString());
        //configObj.addProperty("oreVeinModifierExistingOres", targetConfig.oreVeinModifier);
        
        configArray.add(configObj);
        resp.add("value", configArray);

        return resp;
    }



    /**
     * Locate Ore Clusters in a level, optionally filtering by oreType and limiting the number of clusters
     * @param level
     * @param pos
     * @param oreType
     * @param limit
     * @return null if level or pos is null, or limit is less than 1
     * locateClusters
     */
    public List<OreClusterInfo> locateOreClusters(LevelAccessor level, BlockPos pos, BlockState oreType, int limit)
    {
        //1. Check if level is valid and get OreClusterManager for the level
        if(level == null)
            return null;

        if(pos == null)
            return null;

        if(limit <= 0)
            return null;

        OreClusterManager manager = managers.get(level);
        if(manager == null)
            return null;

        //2. Get list of all oreClusters
        Map<OreClusterId, Set<String>> clusters = manager.getExistingClustersByType();

        LoggerProject.logInfo(null, "008000", "Found " + clusters.size() +
         " clusters in level: " + HBUtil.LevelUtil.toLevelId(level) + " with oreType: " + ((oreType==null) ? "any" : oreType) );

        //3. Create list of all valid Clusters from each chunk, filtering by oreType if necessary
        List<String> validClusterChunkIds = new ArrayList<>();
        if(oreType == null)
        {
            for(OreClusterId clusterType : clusters.keySet()) {
                validClusterChunkIds.addAll(clusters.get(clusterType));
            }
        }
        else
        {
            Set<String> clusterChunkIds = clusters.get(oreType);
            if(clusterChunkIds != null)
                validClusterChunkIds.addAll(clusterChunkIds);
        }

        //4. Add clusters to clusterInfo
        List<OreClusterInfo> clusterInfo = new ArrayList<>();
        for(String chunkId : validClusterChunkIds)
        {
            ManagedOreClusterChunk cluster = manager.getManagedOreClusterChunk(chunkId);
            if(cluster != null && cluster.hasClusters()) {
                cluster.getClusterTypes().forEach((k,v) -> {
                if(v == null)  return;
                if( oreType != null && !k.equals(oreType) ) return;
                clusterInfo.add(new OreClusterInfo(cluster, k));
                });
            }
        }

        LoggerProject.logInfo(null, "008001", "Found " + clusterInfo.size() + " clusters of type: " + oreType);

        //4. Determine distance of each cluster from pos
        for(OreClusterInfo cluster : clusterInfo) {
            cluster.calcPointDistance(pos);
        }

        //5. Sort clusters by distance

        Comparator<OreClusterInfo> clusterComparator = Comparator.comparing(c -> c.pointDistance);
        List<OreClusterInfo> sortedClusters = clusterInfo.stream()
            .sorted( clusterComparator )
            .limit(limit)
            .toList();

        LoggerProject.logInfo(null, "008002", "Sorted clusters by distance from: " + pos + " with limit  " + limit);

        return sortedClusters;
    }

    public JsonObject getManagedChunkDetails(LevelAccessor level, String chunkId) {
        OreClusterManager manager = managers.get(level);
        if(manager == null) return null;
        ManagedOreClusterChunk chunk = manager.getManagedOreClusterChunk(chunkId);
        if(chunk == null) return null;

        //Collect info on ManagedOreClusterChunk
        //id
        //Status
        //Clusters: blockState : position

        String id = chunk.getId();
        JsonObject chunkDetails = new JsonObject();
        chunkDetails.addProperty("id", id);
        chunkDetails.addProperty("status", chunk.getStatus().toString());
        if(chunk.hasClusters()) {
            JsonArray clusterArray = new JsonArray();
            chunk.getClusterTypes().entrySet().forEach( e -> {
                String block = HBUtil.BlockUtil.blockToString(e.getKey().getBlock());
                String pos = e.getValue().toString();
                clusterArray.add(block + ": " + pos);
            });
            chunkDetails.add("clusters", clusterArray);
        } else {
            chunkDetails.addProperty("clusters", "No clusters found");
        }

        return chunkDetails;
    }

    public boolean forceChunkReload(LevelAccessor level, String chunkId) {
        OreClusterManager manager = managers.get(level);
        if(manager == null) return false;
        return manager.forceProcessChunk(chunkId);
    }

    public boolean addCluster(LevelAccessor level, String configId, BlockPos pos) {
        OreClusterId id = modConfig.getOreConfigId( Integer.parseInt(configId) );
        if(id == null) {
            LoggerProject.logWarning("008003", "Could not find config for id: " + configId);
            return false;
        }

        return addCluster(level, id, pos);
    }

    /**
     * Add a cluster at a specified point
     * @param oreType
     * @param pos
     * @return
     */
    public boolean addCluster(LevelAccessor level, OreClusterId oreType, BlockPos pos)
    {
        if(oreType == null || pos == null) return false;
        OreClusterManager manager = managers.get(level);
        if(manager == null) return false;

        String chunkId = HBUtil.ChunkUtil.getId(pos);
        return manager.addNewCluster(oreType, chunkId, pos);
    }


    /**
     * Triggers all clusters in the world to regenerate their clusters
     */
    public void triggerRegen() {
        this.regenManager.triggerRegen();
    }

    /**
     * Triggers a specific cluster to regenerate
     * @param level
     * @param chunkId
     */
    public void triggerRegen(LevelAccessor level, String chunkId) throws InvalidId {
    //Thread it and join after 10 seconds
        if(level == null || chunkId == null) return;
        if(!managers.containsKey(level)) return;
        regenManager.triggerRegen(level, chunkId);
    }

    /**
     * Returns queue lengths and average time of completion for processes in the OreClusterManager
     * @param m
     * @return
     */
    public JsonObject healthCheckStatistics(OreClusterManager m) {
        JsonObject health = new JsonObject();

        // Queue Sizes
        JsonObject queueSizes = new JsonObject();
        queueSizes.addProperty("pendingHandling", m.chunksPendingHandling.size());
        queueSizes.addProperty("pendingDeterminations", m.chunksPendingDeterminations.size());
        queueSizes.addProperty("pendingCleaning", m.chunksPendingCleaning.size());
        queueSizes.addProperty("pendingPreGeneration", m.chunksPendingPreGeneration.size());
        queueSizes.addProperty("pendingRegeneration", m.chunksPendingRegeneration.size());
        health.add("queueSizes", queueSizes);

        // Thread Times
        JsonObject threadTimes = new JsonObject();
        m.THREAD_TIMES.forEach((threadName, times) -> {
            if (!times.isEmpty()) {
                double avg = times.stream().mapToLong(Long::valueOf).average().orElse(0.0);
                threadTimes.addProperty(threadName, avg);
            }
        });
        health.add("averageThreadTimes", threadTimes);

        // Chunk Tracking
        JsonObject chunkTracking = new JsonObject();
        String[] determinedSourceChunks = m.determinedSourceChunks.toArray(new String[0]);
        chunkTracking.add("determinedSourceChunks", HBUtil.FileIO.arrayToJson(determinedSourceChunks));
        chunkTracking.addProperty("determinedChunks", m.determinedChunks.size());
        chunkTracking.addProperty("loadedOreClusterChunks", m.loadedOreClusterChunks.size());
        chunkTracking.addProperty("expiredChunks", m.expiredChunks.size());
        chunkTracking.addProperty("completeChunks", m.completeChunks.size());
        health.add("chunkTracking", chunkTracking);

        return health;
    }


    /**
     * Returns a set of chunkIds for all chunks that have not completed initial processing
     * @param m
     * @return
     */
    public Set<String> getIncompleteChunks(OreClusterManager m)
    {
        Set<String> loaded = m.loadedOreClusterChunks.values().stream()
            .filter( c -> !c.isFinished(c) )
            .map( c -> c.getId() )
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        Set<String> unloaded = m.determinedChunks.stream()
            .filter( c -> !m.completeChunks.contains(c) )
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        loaded.addAll(unloaded);
        Set<String> incompleteChunks = loaded.stream()
            .filter( c -> !m.forceLoadedChunks.containsKey(c))
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        return incompleteChunks;
    }

    public boolean debugForceLoadChunk(OreClusterManager m, String chunkId, AtomicBoolean succeeded) {
        if(  m.forceProcessChunk(chunkId) ) {
            succeeded.set(true);
        } else {
            LoggerProject.logWarning("016004", "Chunk: " + chunkId + " failed to reload properly, maybe try restarting the server");
        }
        return succeeded.get();
    }

}
