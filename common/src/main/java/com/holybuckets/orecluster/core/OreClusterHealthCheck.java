package com.holybuckets.orecluster.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.server.ServerStartedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.holybuckets.orecluster.OreClustersAndRegenMain.DEBUG;
import static java.lang.Thread.sleep;

public class OreClusterHealthCheck {
    private static OreClusterHealthCheck INSTANCE;
    private final Gson gson;
    private OreClusterApi oreClusterApi;
    private final Map<LevelAccessor, OreClusterManager> managers;
    private final Deque<Pair<OreClusterManager, String>> chunkReloadTasks;

    //Threads
    private Thread statisticHealthCheckThread;
    private Thread chunkLoadedHealthCheckThread;
    private final ThreadPoolExecutor chunkReloadExecutor;


    public OreClusterHealthCheck(EventRegistrar reg, OreClusterApi api, Map<LevelAccessor, OreClusterManager> managers)
    {
        this.oreClusterApi = api;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.managers = managers;
        this.chunkReloadTasks = new LinkedList<>();

        this.chunkReloadExecutor = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());

        if (DEBUG) {
            reg.registerOnServerTick(TickType.ON_1200_TICKS, this::onDailyTick);
        } else {
            reg.registerOnDailyTick(GeneralConfig.OVERWORLD_LOC, this::onDailyTick);
        }
        reg.registerOnServerStarted(this::onServerStarted);

        INSTANCE = this;
    }

    //* BEHAVIOR

    /**
     * Logs the size of all queues for each level manager, average processing time
     */
    private void statisticHealthCheck()
    {
        //LoggerProject.logInfo("016001", "Starting statistic healthCheck");
        try {
            for (OreClusterManager m : managers.values()) {
                JsonElement jsonHealthCheck = oreClusterApi.healthCheckStatistics(m);
                StringBuilder message = new StringBuilder("Manager Health Check for level: ");
                message.append(HBUtil.LevelUtil.toLevelId(m.getLevel()));
                message.append("\n\n");
                message.append(gson.toJson(jsonHealthCheck));
                LoggerProject.logInfo("001001", message.toString());
            }
        } catch (Exception e) {
            LoggerProject.logWarning("061002", "Manager Health Check Thread Exception: " + e.getMessage());
        }
        finally {
            this.statisticHealthCheckThread = null;
        }
    }

    private static final long CHUNK_LOADS_WAIT_PROCESSING_TIME = (OreClustersAndRegenMain.DEBUG) ? 30000 : 60000;
    HBUtil.ChunkUtil c_util = new HBUtil.ChunkUtil();
    private void chunkLoadsHealthCheck()
    {
        for(OreClusterManager m : managers.values())
        {
            GeneralConfig config = GeneralConfig.getInstance();
            List<ServerPlayer> players = config.getServer().getPlayerList().getPlayers();
            List<String> playerChunks = players.stream()
                    .filter(p -> p.serverLevel() == m.getLevel())
                    .map(p -> c_util.getId(p.chunkPosition())).toList();

            List<String> localAreaChunks = new ArrayList<>(playerChunks.size()*10);

            playerChunks.stream().map(c -> c_util.getChunkPos(c)).forEach( cp -> {
                localAreaChunks.addAll(c_util.getLocalChunkIds(cp,1));
            });

            Set<String> incompleteChunks = oreClusterApi.getIncompleteChunks(m);
            List<String> localIncompleteChunks = incompleteChunks.stream().filter(c -> localAreaChunks.contains(c)).toList();

            if( localIncompleteChunks.isEmpty() ) continue;

            try {
                sleep(CHUNK_LOADS_WAIT_PROCESSING_TIME);
            } catch (InterruptedException e) {
                return;
            }

            incompleteChunks = oreClusterApi.getIncompleteChunks(m);
            incompleteChunks.stream().filter(c -> localIncompleteChunks.contains(c))
                .forEach(c -> chunkReloadTasks.addLast(Pair.of(m, c)));

            chunkReloadExecutor.submit(this::chunkLoadedHealthCheckExecutorThread);
        }

    }
    //END chunksLoadHealthCheck


    private void chunkLoadedHealthCheckExecutorThread()
    {
        try
        {
            while( !chunkReloadTasks.isEmpty() )
            {
                Pair<OreClusterManager, String> task = chunkReloadTasks.poll();
                OreClusterManager m = task.getLeft();
                String chunkId = task.getRight();
                AtomicBoolean succeeded = new AtomicBoolean(false);

                LoggerProject.logWarning("016003", "Chunk: " + chunkId + " failed to load properly, will be reloaded");

                Thread forceLoad = new Thread(() -> oreClusterApi.debugForceLoadChunk(m, chunkId, succeeded));
                forceLoad.start();
                forceLoad.join(10_000);
            }
        }
        catch (InterruptedException e)
        {
            LoggerProject.logError("002003","OreClusterManager::onNewlyAddedChunk() thread interrupted: "
                + e.getMessage());
        }
    }

    private void chunkLoadedHealthCheckWatchThread()
    {
        try {
            while(managers.size() > 0) {
                sleep(1000);
                //chunkLoadsHealthCheck();
            }
        } catch (InterruptedException e) {
        }

    }



    //* General
    public void shutdown()
    {
        if (this.statisticHealthCheckThread != null) {
            this.statisticHealthCheckThread.interrupt();
            this.statisticHealthCheckThread = null;
        }
        if (this.chunkLoadedHealthCheckThread != null) {
            this.chunkLoadedHealthCheckThread.interrupt();
            this.chunkLoadedHealthCheckThread = null;
        }

        this.chunkReloadExecutor.shutdown();
    }


    //* EVENTS
    private void onServerStarted(ServerStartedEvent event) {
        if (this.chunkLoadedHealthCheckThread == null) {
            this.chunkLoadedHealthCheckThread = new Thread(this::chunkLoadedHealthCheckWatchThread, "OreClusterAndRegenMain-ChunkLoadedHealthCheck");
            this.chunkLoadedHealthCheckThread.start();
        }
    }

    private void onDailyTick(ServerTickEvent event) {
        if (this.statisticHealthCheckThread == null) {
            this.statisticHealthCheckThread = new Thread(this::statisticHealthCheck, "OreClusterAndRegenMain-ManagerHealthCheck");
            this.statisticHealthCheckThread.start();
        }
    }



}
//END CLASS
