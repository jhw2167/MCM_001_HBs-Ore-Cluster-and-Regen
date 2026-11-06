package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class OreFeaturePlacementManager {

    public static final String CLASS_ID = "011";
    static final Map<ServerLevel, OreFeaturePlacementManager> MANAGERS = new ConcurrentHashMap<>();
    static ModRealTimeConfig CONFIG;

    private final ServerLevel level;
    private final Random random;
    private volatile Map<Biome, Map<Block, Float>> frequencies = Map.of();

    public OreFeaturePlacementManager(ServerLevel level, long seed) {
        this.level = level;
        this.random = new Random(seed);
        LoggerProject.logInit("011000", this.getClass().getName());
    }

    public static void init(EventRegistrar reg, ModRealTimeConfig config) {
        CONFIG = config;
        reg.registerOnLevelLoad(OreFeaturePlacementManager::onLevelLoad, EventPriority.Normal);
        reg.registerOnServerStopped(OreFeaturePlacementManager::onServerStopped, EventPriority.Low);
    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        OreFeaturePlacementManager manager = MANAGERS.computeIfAbsent(level, l -> new OreFeaturePlacementManager(level, levelSeed(level)));
        manager.setFrequencies(CONFIG.getOreFeatureBiomeMatrix().getFrequencies());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MANAGERS.clear();
    }

    private static long levelSeed(ServerLevel level) {
        Long subSeed = CONFIG.getDefaultConfigModel().subSeed;
        long seed = level.getSeed() ^ level.dimension().location().hashCode();
        return subSeed == null ? seed : seed ^ subSeed;
    }

    public static OreFeaturePlacementManager getManager(ServerLevel level) {
        return MANAGERS.get(level);
    }

    public void setFrequencies(Map<Biome, Map<Block, Float>> frequencies) {
        Map<Biome, Map<Block, Float>> copy = new HashMap<>();
        frequencies.forEach((b, m) -> copy.put(b, Map.copyOf(m)));
        this.frequencies = Map.copyOf(copy);
    }

    public Map<Biome, Map<Block, Float>> getFrequencies() {
        return frequencies;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public int rollPlacements(BlockPos origin, PlacedFeature feature) {
        float frequency = getFrequency(origin, feature);
        if (frequency == 1f) return 1;
        int whole = (int) frequency;
        return random.nextFloat() < frequency - whole ? whole + 1 : whole;
    }

    public float getFrequency(BlockPos origin, PlacedFeature feature) {
        if (!(feature.feature().value().config() instanceof OreConfiguration ore)) return 1f;
        var b = HBUtil.LevelUtil.getBiomeAtPoint(this.level, origin);
        Map<Block, Float> biomeFrequencies = frequencies.get(b.value());
        if (biomeFrequencies == null) return 1f;
        for (OreConfiguration.TargetBlockState target : ore.targetStates) {
            Float f = biomeFrequencies.get(target.state.getBlock());
            if (f != null) return Math.max(0f, f);
        }
        return 1f;
    }


}
