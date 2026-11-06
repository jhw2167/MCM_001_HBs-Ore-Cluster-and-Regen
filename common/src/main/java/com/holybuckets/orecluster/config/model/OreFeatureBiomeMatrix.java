package com.holybuckets.orecluster.config.model;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public class OreFeatureBiomeMatrix {

    public static final float DEFAULT_FREQUENCY = 1f;
    public static final OreFeatureBiomeMatrix EMPTY = new OreFeatureBiomeMatrix(Map.of());

    private final Map<Biome, Map<Block, Float>> frequencies;

    public OreFeatureBiomeMatrix(Map<Biome, Map<Block, Float>> frequencies) {
        Map<Biome, Map<Block, Float>> sealed = new HashMap<>();
        frequencies.forEach((biome, ores) -> sealed.put(biome, Map.copyOf(ores)));
        this.frequencies = Map.copyOf(sealed);
    }

    public float getFrequency(Biome biome, Block ore) {
        Map<Block, Float> ores = frequencies.get(biome);
        if (ores == null) return DEFAULT_FREQUENCY;
        return ores.getOrDefault(ore, DEFAULT_FREQUENCY);
    }

    public Map<Biome, Map<Block, Float>> getFrequencies() {
        return frequencies;
    }

    public Map<Block, Float> getFrequencies(Biome biome) {
        return frequencies.getOrDefault(biome, Map.of());
    }

    public int size() {
        return frequencies.size();
    }

}
