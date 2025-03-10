package com.holybuckets.orecluster.core;


import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import com.holybuckets.orecluster.core.model.OreClusterInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Description: Class designed for interfacing with OreClusterManager and perform lookup operations such as:
 *      - Locate Ore Clusters
 *      - Locate Ore
 *      - Count Ore Clusters
 */
public class OreClusterInterface {

    public static final String CLASS_ID = "008";
    private static OreClusterInterface INSTANCE;

    public static OreClusterInterface initInstance( Map<LevelAccessor, OreClusterManager> managers ) {
        if(INSTANCE == null)
            INSTANCE = new OreClusterInterface(managers);
        return INSTANCE;
    }

    public static boolean isInit() {
        return INSTANCE != null;
    }

    /**
     * Gets instance of OreClusterInterface, returns null if instance is not initialized
     * @return
     */
    public static OreClusterInterface getInstance() {
        if(INSTANCE == null)
            return null;
        return INSTANCE;
    }

    private final Map<LevelAccessor, OreClusterManager> MANAGERS;
    private OreClusterInterface( Map<LevelAccessor, OreClusterManager> managers ) {
        this.MANAGERS = managers;
    }

    //Implement locateOreClusters method with parameters such as
            //- LevelAccessor - find ore clusters in this level
            //- BlockPos - find ore clusters near this position
            //- Block oreType - find ore clusters of this type
            // - int limit - limit the number of ore clusters to find

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

        OreClusterManager manager = MANAGERS.get(level);
        if(manager == null)
            return null;

        //2. Get list of all oreClusters
        Map<BlockState, Set<String>> clusters = manager.getExistingClustersByType();

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


    public static JsonObject healthCheck(OreClusterManager m) {
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
        chunkTracking.addProperty("determinedSourceChunks", HBUtil.FileIO.arrayToJson(determinedSourceChunks).toString());
        chunkTracking.addProperty("determinedChunks", m.determinedChunks.size());
        chunkTracking.addProperty("loadedOreClusterChunks", m.loadedOreClusterChunks.size());
        chunkTracking.addProperty("expiredChunks", m.expiredChunks.size());
        chunkTracking.addProperty("completeChunks", m.completeChunks.size());
        health.add("chunkTracking", chunkTracking);

        return health;
    }


}
