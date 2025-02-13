package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.datastructure.ConcurrentLinkedSet;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Description: Tracks BlockState updates to a chunk before the chunk is full
 *  First collects all relevant ores placed in a chunk,
 * then attempts
 */
public class OreClusterBlockStateTracker
{
    LevelAccessor currentLevel;
    OreClusterManager manager;
    Map<BlockState, OreClusterConfigModel> trackingOres;

    static Map<LevelChunkSection, LevelChunkAccess> sectionMap = new ConcurrentHashMap<>();
    static Map<LevelChunkSection, List<BlockStateUpdate>> blockStateUpdates = new ConcurrentHashMap<>();
    static Set<ChunkAccess> chunks = new ConcurrentLinkedSet<>();

    static Map<BlockState, OreClusterConfigModel> staticTrackingOres = new HashMap<>();
    static final Map<LevelAccessor, OreClusterBlockStateTracker> MAP = new HashMap<>();

    static Thread threadRemoveFullStatusChunks;


    private OreClusterBlockStateTracker(LevelAccessor level)
    {
        this.currentLevel = level;
        this.manager = OreClusterManager.getManager(level);

        Map<Block, OreClusterConfigModel> map = manager.getConfig().getOreConfigs();
        this.trackingOres = map.entrySet().stream().collect(
            java.util.stream.Collectors.toMap(
                e -> e.getKey().defaultBlockState(),
                e -> e.getValue()
            )
        );
        staticTrackingOres.putAll(this.trackingOres);
    }

    public static void addTrackingChunk(ServerLevel level, ChunkAccess chunk, BlockPos pos)
    {
        if( chunks.contains(chunk) ) return;
        chunks.add(chunk);
        Arrays.stream(chunk.getSections()).toList().forEach(section -> sectionMap.put(section, new LevelChunkAccess(chunk, level)));

        String chunkId = HBUtil.ChunkUtil.getId(chunk);
        String dimensionId = level.dimension().location().toString();
        LoggerProject.logInfo("099001", "Adding tracking chunk: " + chunkId + " in dimension: " + dimensionId);
    }

    public static void enqueueBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( !staticTrackingOres.containsKey(state) ) return;

        boolean runUpdate = false;
        BlockStateUpdate update = new BlockStateUpdate(section, state, new HBUtil.TripleInt(x, y, z));

        if( sectionMap.containsKey(section) )
        {
            LevelChunkAccess chunk = sectionMap.get(section);
            String chunkId = HBUtil.ChunkUtil.getId(chunk.chunk);
            ManagedOreClusterChunk managedChunk = OreClusterManager.getManager(chunk.level).getDeterminedOreClusterChunk(chunkId);
            if(managedChunk != null)
                runUpdate = true;
        }

        if( runUpdate )
        {
            LevelChunkAccess chunk = sectionMap.get(section);
            OreClusterBlockStateTracker tracker = MAP.get(chunk.level);
            tracker.mapBlockState(chunk, update);
            if(blockStateUpdates.containsKey(section))
                blockStateUpdates.get(section).forEach(u -> tracker.mapBlockState(chunk, u));
            blockStateUpdates.remove(section);
        } else {
            if( !blockStateUpdates.containsKey(section) )
                blockStateUpdates.put(section, new ArrayList<>());
            blockStateUpdates.get(section).add(update);

        }

    }

    private boolean mapBlockState(LevelChunkAccess chunk, BlockStateUpdate update)
    {
        //Get the ManagedOreClusterChunk from the OreClusterManager, add the update using HBUtil.WorldPos
        String chunkId = HBUtil.ChunkUtil.getId(chunk.chunk);
        ManagedOreClusterChunk managedChunk = manager.getDeterminedOreClusterChunk(chunkId);
        if( managedChunk == null ) return false;

        int sectionNum = Arrays.stream(chunk.chunk.getSections()).toList().indexOf(update.section);
        HBUtil.WorldPos worldPos = new HBUtil.WorldPos(update.relativePos, sectionNum, chunk.chunk);
        managedChunk.addOre(update.state, worldPos.getWorldPos() );
        return true;
    }


    //* LEVEL EVENTS
    public static void init(EventRegistrar reg) {
        reg.registerOnLevelLoad(OreClusterBlockStateTracker::onLevelLoad, EventPriority.High );
        reg.registerOnLevelUnload(OreClusterBlockStateTracker::onLevelUnload, EventPriority.Low );
    }
    private static void onLevelLoad(LevelLoadingEvent.Load event)
    {
        if( event.getLevel().isClientSide() ) return;
        LevelAccessor level = event.getLevel();
        MAP.put(level, new OreClusterBlockStateTracker(level));
        if(threadRemoveFullStatusChunks == null) {
            threadRemoveFullStatusChunks = new Thread(() -> manageThreadRemoveFullStatusChunks());
            threadRemoveFullStatusChunks.start();
        }
    }

    private static void onLevelUnload(LevelLoadingEvent.Unload event)
    {
        if( event.getLevel().isClientSide() ) return;
        LevelAccessor level = event.getLevel();
        MAP.remove(level);
        if(threadRemoveFullStatusChunks != null) {
            threadRemoveFullStatusChunks.interrupt();
            threadRemoveFullStatusChunks = null;
        }
    }

    //Write a method to manage threadRemoveFullStatusChunks
    private static void manageThreadRemoveFullStatusChunks()
    {
        while(true)
        {
            try {
                threadRemoveFullStatusChunks();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }


    private static void threadRemoveFullStatusChunks()
    {
        List<LevelChunkAccess> finished = new ArrayList<>();
         sectionMap.values().stream().forEach(chunk -> {
             if (chunk.chunk.getStatus().isOrAfter(ChunkStatus.FULL))
                 finished.add(chunk);
         });

         for( LevelChunkAccess chunk : finished )
         {
            OreClusterBlockStateTracker tracker = MAP.get(chunk.level);
            for( LevelChunkSection section : chunk.chunk.getSections() ) {
                //runAll remaining updates
                if( blockStateUpdates.containsKey(section) )
                    blockStateUpdates.get(section).forEach(update -> tracker.mapBlockState(chunk, update));
                sectionMap.remove(section);
            }
         }

            chunks.removeAll(finished.stream().map(c -> c.chunk).toList());

    }

    /**
     * Need to preserve the blockStateInfo from any feature
     */
    static class BlockStateUpdate {

        LevelChunkSection section;
        BlockState state;
        HBUtil.TripleInt relativePos;

        //make a constructor
        BlockStateUpdate(LevelChunkSection section, BlockState state, HBUtil.TripleInt relativePos) {
            this.section = section;
            this.state = state;
            this.relativePos = relativePos;
        }

    }


    static class LevelChunkAccess {

        ChunkAccess chunk;
        LevelAccessor level;

        LevelChunkAccess(ChunkAccess chunk, LevelAccessor level) {
            this.chunk = chunk;
            this.level = level;
        }
    }

}
