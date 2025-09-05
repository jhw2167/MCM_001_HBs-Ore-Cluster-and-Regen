package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

/**
 * Description: Tracks BlockState updates to a chunk before the chunk is full
 *  First collects all relevant ores placed in a chunk,
 * then attempts
 */
public class OreClusterBlockStateTracker
{
    ChunkAccess currentChunk;
    Map<LevelChunkSection, Integer> chunkSections;
    String chunkId;
    ServerLevel currentLevel;
    ManagedOreClusterChunk currentManagedOreClusterChunk;

    static ModRealTimeConfig CONFIG;
    static Map<Integer, ServerLevel> LEVELCHUNKSECTION_LEVEL_REF_MAP;
    static Map<ServerLevel, OreClusterBlockStateTracker> LEVEL_TRACKERS;
    static Map<OreClusterId, OreClusterConfigModel> trackingOreConfig;

    public static void init(ModRealTimeConfig modRealTimeConfig) {
        LEVEL_TRACKERS = new ConcurrentHashMap<>();
        LEVELCHUNKSECTION_LEVEL_REF_MAP = Collections.synchronizedMap(new LinkedHashMap<>());
        CONFIG = modRealTimeConfig;
    }

    public static void setTrackingChunk(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        if( !LEVEL_TRACKERS.containsKey(level) ) {
            LEVEL_TRACKERS.put(level, new OreClusterBlockStateTracker(level));
        }
        LEVEL_TRACKERS.get(level).setLevelTrackingChunk(chunk, pos);
    }

    public static void trackBlockState(LevelChunkSection section, BlockState state, int x, int y, int z) {
        if(LEVELCHUNKSECTION_LEVEL_REF_MAP.containsKey(section.hashCode()) == false) return;
        ServerLevel level = LEVELCHUNKSECTION_LEVEL_REF_MAP.get(section.hashCode());
        LEVEL_TRACKERS.get(level).trackLevelBlockState(section, state, x, y, z);
    }

    //** CORE

    //write a constructor
    public OreClusterBlockStateTracker(ServerLevel level) {
        this.currentLevel = level;
        if( trackingOreConfig == null )
            trackingOreConfig = CONFIG.getOreConfigs();
    }

    private static final int MAX_CHUNK_REFS = (int) Math.pow(2, 16); //64k
    private static final int MAX_CHUNK_REFS_CLEAR = (int) Math.pow(2, 14); //16k
    private static int chunkCount = 0;
    public void setLevelTrackingChunk(ChunkAccess chunk, BlockPos pos)
    {
        chunkCount++;
        currentChunk = chunk;
        chunkSections = new ConcurrentHashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        Arrays.stream(chunk.getSections()).forEach((section) -> {
            chunkSections.put(section, count.getAndIncrement());
            LEVELCHUNKSECTION_LEVEL_REF_MAP.put(section.hashCode(), currentLevel);
        });

        if( chunkCount > MAX_CHUNK_REFS ) {
            chunkCount = 0;
            //Clear out old entries
            Iterator<Map.Entry<Integer, ServerLevel>> it = LEVELCHUNKSECTION_LEVEL_REF_MAP.entrySet().iterator();
            int clearCount = 0;
            while (it.hasNext() && clearCount < MAX_CHUNK_REFS_CLEAR) {
                it.next();
                it.remove();
                clearCount++;
            }
        }

        OreClusterManager manager = OreClusterManager.getManager(currentLevel);
        if( manager == null ) return;
        chunkId = HBUtil.ChunkUtil.getId(currentChunk);
        if( manager.getDeterminedOreClusterChunk( chunkId ) == null) return;
        currentManagedOreClusterChunk = manager.getDeterminedOreClusterChunk( chunkId );
        chunk.getSections()[0].getBiomes().getAll( hb -> {
            currentManagedOreClusterChunk.addBiome(hb.value());
        });
    }


    //This will be called before features start being placed, (before there is a chunk to track)
    private static long blockCount=0;
    public void trackLevelBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        if( trackingOreConfig == null ) return;
        if( currentManagedOreClusterChunk == null ) return;

        BlockState defaultState = state.getBlock().defaultBlockState();
        if( !CONFIG.maybeHasBlock(defaultState) ) return;

        ManagedOreClusterChunk chunk = currentManagedOreClusterChunk;
        Biome localBiome = section.getNoiseBiome(x, 0, z).value();
        OreClusterId configId = chunk.chooseConfigId(this.currentLevel,localBiome, defaultState, CONFIG);
        if( configId == null ) return;
        OreClusterConfigModel config = trackingOreConfig.get(configId);
        if( !ModRealTimeConfig.doesLevelMatch(config, currentLevel) ) return;
        if( !ModRealTimeConfig.clustersDoSpawn(config) ) return;

        blockCount++;
        int secIndex = 0;
        if( chunkSections.containsKey(section) ) {
            secIndex = chunkSections.get(section);
        } else {
            return;
        }
        int secY = currentLevel.getSectionYFromSectionIndex(secIndex);
        if( !chunk.sampleAddOre(configId, secY) ) return;

        HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
        HBUtil.WorldPos pos = new HBUtil.WorldPos( relativePos, secIndex, currentChunk );
        chunk.addOre(configId, pos.getWorldPos(), true);
    }


}
