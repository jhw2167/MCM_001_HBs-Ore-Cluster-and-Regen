package com.holybuckets.orecluster.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.OreClustersAndRegenMain;

public class OreClusterHealthCheck {
    private static OreClusterHealthCheck INSTANCE;
    private final Gson gson;

    private OreClusterHealthCheck() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static OreClusterHealthCheck getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new OreClusterHealthCheck();
        }
        return INSTANCE;
    }

    public void performHealthCheck() {
        try {
            for (OreClusterManager m : OreClustersAndRegenMain.getManagers().values()) {
                JsonElement jsonHealthCheck = getHealthCheckData(m);
                StringBuilder message = new StringBuilder("Manager Health Check for level: ");
                message.append(HBUtil.LevelUtil.toLevelId(m.getLevel()));
                message.append("\n\n");
                message.append(gson.toJson(jsonHealthCheck));
                LoggerProject.logInfo("001001", message.toString());
            }
        } catch (Exception e) {
            LoggerProject.logWarning("001002", "Manager Health Check Thread Exception: " + e.getMessage());
        }
    }

    public JsonObject getHealthCheckData(OreClusterManager m) {
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
}
