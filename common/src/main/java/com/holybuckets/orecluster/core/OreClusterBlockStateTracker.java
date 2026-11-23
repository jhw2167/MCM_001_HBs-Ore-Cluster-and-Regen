package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

public class OreClusterBlockStateTracker
{
    private static final ThreadLocal<TrackingContext> CURRENT = new ThreadLocal<>();
    private static final Set<ManagedOreClusterChunk> BIOMES_LOADED =
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static volatile ModRealTimeConfig CONFIG;
    private static volatile Map<OreClusterId, OreClusterConfigModel> trackingOreConfig;
    private static volatile long generation = 0;

    private static final class TrackingContext
    {
        final ServerLevel level;
        final ChunkAccess chunk;
        final LevelChunkSection[] sections;
        final ManagedOreClusterChunk managedChunk;
        final TrackingContext previous;
        final long generation;
        int depth = 1;

        TrackingContext(ServerLevel level, ChunkAccess chunk, ManagedOreClusterChunk managedChunk, TrackingContext previous, long generation) {
            this.level = level;
            this.chunk = chunk;
            this.sections = chunk.getSections();
            this.managedChunk = managedChunk;
            this.previous = previous;
            this.generation = generation;
        }

        int indexOf(LevelChunkSection section) {
            for (int i = 0; i < sections.length; i++) {
                if (sections[i] == section) return i;
            }
            return -1;
        }
    }

    public static void init(ModRealTimeConfig modRealTimeConfig) {
        CONFIG = modRealTimeConfig;
    }

    public static void reset() {
        generation++;
        trackingOreConfig = null;
        BIOMES_LOADED.clear();
        CURRENT.remove();
    }

    public static void beginTracking(ServerLevel level, ChunkAccess chunk)
    {
        if (CONFIG == null || level == null || chunk == null) return;

        TrackingContext current = CURRENT.get();
        if (current != null && current.generation != generation) current = null;
        if (current != null && current.chunk == chunk) {
            current.depth++;
            return;
        }

        if (trackingOreConfig == null) trackingOreConfig = CONFIG.getOreConfigs();
        CURRENT.set(new TrackingContext(level, chunk, managedChunkFor(level, chunk), current, generation));
    }

    public static void endTracking()
    {
        TrackingContext current = CURRENT.get();
        if (current == null) return;
        if (--current.depth > 0) return;

        TrackingContext previous = current.previous;
        if (previous == null || previous.generation != generation) CURRENT.remove();
        else CURRENT.set(previous);
    }

    private static ManagedOreClusterChunk managedChunkFor(ServerLevel level, ChunkAccess chunk)
    {
        OreClusterManager manager = OreClusterManager.getManager(level);
        if (manager == null) return null;

        ManagedOreClusterChunk managed = manager.getManagedOreClusterChunk(HBUtil.ChunkUtil.getId(chunk));
        if (managed != null && BIOMES_LOADED.add(managed)) managed.loadBiomes(chunk);
        return managed;
    }

    public static void trackBlockState(LevelChunkSection section, BlockState state, int x, int y, int z)
    {
        TrackingContext ctx = CURRENT.get();
        if (ctx == null || ctx.managedChunk == null) return;
        if (ctx.generation != generation) {
            CURRENT.remove();
            return;
        }

        Map<OreClusterId, OreClusterConfigModel> oreConfigs = trackingOreConfig;
        if (oreConfigs == null) return;

        BlockState defaultState = state.getBlock().defaultBlockState();
        if (!CONFIG.maybeHasBlock(defaultState)) return;

        int secIndex = ctx.indexOf(section);
        if (secIndex < 0) return;

        ManagedOreClusterChunk chunk = ctx.managedChunk;
        Biome localBiome = section.getNoiseBiome(x, 0, z).value();
        OreClusterId configId = chunk.chooseConfigId(ctx.level, localBiome, defaultState, CONFIG);
        if (configId == null) return;
        OreClusterConfigModel config = oreConfigs.get(configId);
        if (!ModRealTimeConfig.doesLevelMatch(config, ctx.level)) return;
        if (!ModRealTimeConfig.clustersDoSpawn(config)) return;

        int secY = ctx.level.getSectionYFromSectionIndex(secIndex);
        if (!chunk.sampleAddOre(configId, secY)) return;

        HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
        HBUtil.WorldPos pos = new HBUtil.WorldPos(relativePos, secIndex, ctx.chunk);
        chunk.addOre(configId, pos.getWorldPos(), true);
    }

}
