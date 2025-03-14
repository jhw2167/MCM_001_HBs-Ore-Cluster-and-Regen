package com.holybuckets.orecluster.core;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import com.holybuckets.orecluster.core.model.OreClusterInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static OreClusterApi initInstance(Map<LevelAccessor, OreClusterManager> managers, ModRealTimeConfig modConfig ) {
        if(INSTANCE == null)
            INSTANCE = new OreClusterApi(managers, modConfig);
        return INSTANCE;
    }

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

    private OreClusterApi(Map<LevelAccessor, OreClusterManager> managers, ModRealTimeConfig modConfig ) {
        this.modConfig = modConfig;
        this.managers = managers;
    }


    public JsonObject getConfigSummary()
    {
        JsonObject resp = new JsonObject();
        //Return default config
        OreClusterConfigModel config = modConfig.getDefaultConfig();
        JsonArray allOresArray = new JsonArray();
        for(OreClusterConfigModel ore : modConfig.getOreConfigs().values() )
        {
            JsonObject oreObj = new JsonObject();
            oreObj.addProperty("header", "Ore With ConfigId: " + ore.configId + ":");
            oreObj.addProperty("clusterType", HBUtil.BlockUtil.blockToString(ore.oreClusterType.getBlock()) );
            oreObj.addProperty("clusterSpawnRate", config.oreClusterSpawnRate);
            //oreObj.addProperty("biome", config.oreClusterSpawnRate);
            allOresArray.add(oreObj);
        }

        resp.addProperty("header", "Configured ores:\n");
        resp.add("value", allOresArray);

        return resp;
    }

    public JsonObject getConfig(String configId)
    {
        JsonObject resp = new JsonObject();
        
        // If no configId provided, return summary
        if(configId == null) {
            return getConfigSummary();
        }

        // Get config for specific ID
        Map<BlockState, OreClusterConfigModel> configs = modConfig.getOreConfigs();
        OreClusterConfigModel targetConfig = null;
        BlockState targetOre = null;

        // Find config with matching ID
        for(Map.Entry<BlockState, OreClusterConfigModel> entry : configs.entrySet()) {
            if(entry.getValue().configId.equals(configId)) {
                targetConfig = entry.getValue();
                targetOre = entry.getKey();
                break;
            }
        }

        // Return null if config not found
        if(targetConfig == null) {
            return null;
        }

        resp.addProperty("header", "Config summary for cluster config id: " + configId);
        
        JsonArray configArray = new JsonArray();
        JsonObject configObj = new JsonObject();
        
        // Add ore type first
        configObj.addProperty("oreClusterType", HBUtil.BlockUtil.blockToString(targetOre.getBlock()));
        
        // Add all other config properties
        configObj.addProperty("configId", targetConfig.configId);
        configObj.addProperty("oreClusterSpawnRate", targetConfig.oreClusterSpawnRate);
        configObj.addProperty("oreClusterSize", targetConfig.oreClusterSize);
        configObj.addProperty("oreClusterDoesRegenerate", targetConfig.oreClusterDoesRegenerate);
        configObj.addProperty("oreClusterRegenerationTime", targetConfig.oreClusterRegenerationTime);
        configObj.addProperty("oreVeinModifier", targetConfig.oreVeinModifier);
        
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
        Map<BlockState, Set<String>> clusters = manager.getTentativeClustersByType();

        LoggerProject.logInfo(null, "008000", "Found " + clusters.size() +
         " clusters in level: " + HBUtil.LevelUtil.toLevelId(level) + " with oreType: " + oreType );

        //3. Create list of all valid Clusters from each chunk, filtering by oreType if necessary
        List<String> validClusterChunkIds = new ArrayList<>();
        if(oreType == null)
        {
            for(BlockState clusterType : clusters.keySet()) {
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

    /**
     * Add a cluster at a specified point
     * @param oreType
     * @param pos
     * @return
     */
    public boolean addCluster(LevelAccessor level, BlockState oreType, BlockPos pos)
    {
        if(oreType == null || pos == null) return false;
        OreClusterManager manager = managers.get(level);
        if(manager == null) return false;

        String chunkId = HBUtil.ChunkUtil.getId(pos);
        return manager.addNewCluster(oreType, chunkId, pos);
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
        return loaded;
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
