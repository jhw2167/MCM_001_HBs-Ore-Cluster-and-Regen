package com.holybuckets.orecluster.config.json;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.modelInterface.IStringSerializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OreFeatureBiomeMatrixJsonConfig {

    public static final String DEF_CONFIG_FILE_PATH = "config/HBOreFeatureBiomeMatrixConfig.json";

    public static final String COMMENT_KEY = "comment";
    public static final String COMMENT = "Define each object with a biome name. Inside it add 'namespace:ore_block': relative frequency of that ore's vein features."
        + " 0 removes the ore's veins in that biome, 2 places twice as many. Deepslate variants share the veins of their stone ore."
        + " The biome is read at the surface of each chunk. Unlisted biome and ore pairings default to 1";

    public static final String[] DEFAULT_ORES = {
        "minecraft:coal_ore",
        "minecraft:copper_ore",
        "minecraft:iron_ore",
        "minecraft:gold_ore",
        "minecraft:redstone_ore",
        "minecraft:lapis_ore",
        "minecraft:diamond_ore",
        "minecraft:emerald_ore"
    };

    public static final String[] DEFAULT_NETHER_ORES = {
        "minecraft:nether_gold_ore",
        "minecraft:nether_quartz_ore",
        "minecraft:ancient_debris"
    };

    public static final IStringSerializable DEFAULT_CONFIG = new IStringSerializable() {
        @Override
        public String serialize() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(toJsonObject(buildDefaultConfig()));
        }

        @Override
        public void deserialize(String jsonString) {}
    };

    private final String biomeId;
    private final Map<String, Float> frequencies;

    public OreFeatureBiomeMatrixJsonConfig(String biomeId, Map<String, Float> frequencies) {
        this.biomeId = biomeId;
        this.frequencies = new LinkedHashMap<>(frequencies);
    }

    public OreFeatureBiomeMatrixJsonConfig(String biomeId, JsonObject obj) {
        this.biomeId = biomeId;
        this.frequencies = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            Float value = asFloat(entry.getValue());
            if (value != null) frequencies.put(entry.getKey(), value);
        }
    }

    public String getBiomeId() {
        return biomeId;
    }

    public Map<String, Float> getFrequencies() {
        return Collections.unmodifiableMap(frequencies);
    }

    public JsonObject serialize() {
        JsonObject obj = new JsonObject();
        frequencies.forEach(obj::addProperty);
        return obj;
    }

    public static List<OreFeatureBiomeMatrixJsonConfig> parse(JsonObject root) {
        List<OreFeatureBiomeMatrixJsonConfig> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            entries.add(new OreFeatureBiomeMatrixJsonConfig(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
        return entries;
    }

    public static JsonObject toJsonObject(List<OreFeatureBiomeMatrixJsonConfig> entries) {
        JsonObject root = new JsonObject();
        root.addProperty(COMMENT_KEY, COMMENT);
        for (OreFeatureBiomeMatrixJsonConfig entry : entries) {
            root.add(entry.getBiomeId(), entry.serialize());
        }
        return root;
    }

    public static List<OreFeatureBiomeMatrixJsonConfig> buildDefaultConfig() {
        List<OreFeatureBiomeMatrixJsonConfig> entries = new ArrayList<>();
        entries.add(row("minecraft:plains",                   1.0f,   1.0f,   1.0f,   0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:sunflower_plains",         1.0f,   1.0f,   1.0f,   0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:meadow",                   1.0f,   1.25f,  1.0f,   1.0f,   1.0f,   1.25f,  1.0f,   1.25f));
        entries.add(row("minecraft:cherry_grove",             0.75f,  1.0f,   1.0f,   1.0f,   1.25f,  1.5f,   1.0f,   1.25f));

        entries.add(row("minecraft:forest",                   1.25f,  1.0f,   1.0f,   0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:flower_forest",            1.0f,   1.0f,   1.0f,   0.75f,  1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:birch_forest",             1.25f,  1.0f,   1.0f,   0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:old_growth_birch_forest",  1.25f,  1.0f,   1.0f,   0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:dark_forest",              1.5f,   1.0f,   1.0f,   0.75f,  1.25f,  1.0f,   1.0f,   1.0f));

        entries.add(row("minecraft:taiga",                    1.25f,  1.0f,   1.25f,  0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:old_growth_pine_taiga",    1.5f,   1.0f,   1.25f,  0.75f,  1.0f,   1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:old_growth_spruce_taiga",  1.5f,   1.0f,   1.25f,  0.75f,  1.0f,   1.0f,   1.0f,   1.0f));

        entries.add(row("minecraft:snowy_plains",             0.75f,  1.0f,   1.25f,  0.75f,  0.75f,  1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:snowy_taiga",              1.0f,   1.0f,   1.25f,  0.75f,  0.75f,  1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:grove",                    1.0f,   1.0f,   1.5f,   1.0f,   0.75f,  1.25f,  1.0f,   1.5f));
        entries.add(row("minecraft:snowy_slopes",             1.0f,   1.0f,   1.5f,   1.0f,   0.75f,  1.25f,  1.25f,  1.5f));
        entries.add(row("minecraft:ice_spikes",               0.5f,   0.75f,  1.25f,  0.75f,  0.5f,   1.5f,   1.25f,  1.0f));
        entries.add(row("minecraft:frozen_peaks",             1.0f,   1.0f,   1.5f,   1.0f,   0.75f,  1.5f,   1.25f,  2.0f));
        entries.add(row("minecraft:jagged_peaks",             1.0f,   1.0f,   1.5f,   1.0f,   0.75f,  1.25f,  1.25f,  2.0f));
        entries.add(row("minecraft:snowy_beach",              0.75f,  1.0f,   1.0f,   0.75f,  0.75f,  1.0f,   1.0f,   1.0f));
        entries.add(row("minecraft:frozen_river",             0.75f,  1.25f,  1.0f,   1.0f,   0.75f,  1.25f,  1.0f,   1.0f));

        entries.add(row("minecraft:windswept_hills",          1.25f,  1.25f,  1.5f,   1.0f,   1.0f,   1.0f,   1.0f,   1.5f));
        entries.add(row("minecraft:windswept_gravelly_hills", 1.0f,   1.25f,  1.5f,   1.25f,  1.0f,   1.0f,   1.0f,   1.25f));
        entries.add(row("minecraft:windswept_forest",         1.5f,   1.25f,  1.25f,  1.0f,   1.0f,   1.0f,   1.0f,   1.25f));
        entries.add(row("minecraft:stony_peaks",              1.25f,  1.5f,   1.5f,   1.0f,   1.0f,   1.0f,   1.25f,  1.75f));

        entries.add(row("minecraft:savanna",                  0.75f,  1.25f,  1.0f,   1.25f,  1.25f,  0.75f,  1.0f,   1.0f));
        entries.add(row("minecraft:savanna_plateau",          0.75f,  1.25f,  1.0f,   1.25f,  1.25f,  0.75f,  1.0f,   1.0f));
        entries.add(row("minecraft:windswept_savanna",        0.75f,  1.5f,   1.25f,  1.25f,  1.25f,  0.75f,  1.0f,   1.0f));

        entries.add(row("minecraft:desert",                   0.5f,   1.5f,   0.75f,  1.25f,  1.5f,   0.75f,  1.0f,   1.0f));
        entries.add(row("minecraft:badlands",                 0.5f,   1.25f,  1.0f,   1.5f,   1.5f,   0.5f,   1.0f,   1.0f));
        entries.add(row("minecraft:eroded_badlands",          0.5f,   1.25f,  1.0f,   1.75f,  1.5f,   0.5f,   1.0f,   1.0f));
        entries.add(row("minecraft:wooded_badlands",          0.75f,  1.25f,  1.0f,   1.5f,   1.25f,  0.5f,   1.0f,   1.0f));

        entries.add(row("minecraft:jungle",                   1.25f,  1.25f,  0.75f,  1.25f,  1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:sparse_jungle",            1.0f,   1.25f,  0.75f,  1.25f,  1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:bamboo_jungle",            1.25f,  1.25f,  0.75f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));

        entries.add(row("minecraft:swamp",                    1.5f,   1.25f,  0.75f,  0.75f,  1.0f,   1.0f,   0.75f,  1.0f));
        entries.add(row("minecraft:mangrove_swamp",           1.5f,   1.5f,   0.75f,  0.75f,  1.0f,   1.0f,   0.75f,  1.0f));

        entries.add(row("minecraft:river",                    1.0f,   1.25f,  1.0f,   1.25f,  1.0f,   1.0f,   1.0f,   1.0f));

        entries.add(row("minecraft:beach",                    0.75f,  1.0f,   0.75f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:stony_shore",              1.0f,   1.25f,  1.25f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:mushroom_fields",          0.5f,   0.75f,  0.75f,  0.75f,  1.25f,  1.5f,   1.0f,   1.0f));

        entries.add(row("minecraft:warm_ocean",               0.75f,  1.25f,  0.75f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:lukewarm_ocean",           0.75f,  1.0f,   0.75f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:deep_lukewarm_ocean",      0.75f,  1.0f,   0.75f,  1.0f,   1.0f,   1.25f,  1.25f,  1.0f));
        entries.add(row("minecraft:ocean",                    0.75f,  1.0f,   0.75f,  1.0f,   1.0f,   1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:deep_ocean",               0.75f,  1.0f,   0.75f,  1.0f,   1.0f,   1.25f,  1.25f,  1.0f));
        entries.add(row("minecraft:cold_ocean",               0.75f,  1.0f,   1.0f,   1.0f,   0.75f,  1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:deep_cold_ocean",          0.75f,  1.0f,   1.0f,   1.0f,   0.75f,  1.25f,  1.25f,  1.0f));
        entries.add(row("minecraft:frozen_ocean",             0.5f,   0.75f,  1.0f,   0.75f,  0.75f,  1.25f,  1.0f,   1.0f));
        entries.add(row("minecraft:deep_frozen_ocean",        0.5f,   0.75f,  1.0f,   0.75f,  0.75f,  1.25f,  1.25f,  1.0f));

        entries.add(netherRow("minecraft:nether_wastes",      1.0f,   1.0f,   1.0f));
        entries.add(netherRow("minecraft:crimson_forest",     1.25f,  0.75f,  1.0f));
        entries.add(netherRow("minecraft:warped_forest",      0.75f,  1.25f,  1.0f));
        entries.add(netherRow("minecraft:soul_sand_valley",   0.75f,  1.0f,   1.25f));
        entries.add(netherRow("minecraft:basalt_deltas",      1.5f,   1.5f,   1.0f));

        return entries;
    }

    private static OreFeatureBiomeMatrixJsonConfig row(String biomeId, float... frequencies) {
        return row(biomeId, DEFAULT_ORES, frequencies);
    }

    private static OreFeatureBiomeMatrixJsonConfig netherRow(String biomeId, float... frequencies) {
        return row(biomeId, DEFAULT_NETHER_ORES, frequencies);
    }

    private static OreFeatureBiomeMatrixJsonConfig row(String biomeId, String[] ores, float[] frequencies) {
        if (frequencies.length != ores.length) {
            throw new IllegalArgumentException("Default row for " + biomeId + " has " + frequencies.length + " values, expected " + ores.length);
        }
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < frequencies.length; i++) {
            map.put(ores[i], frequencies[i]);
        }
        return new OreFeatureBiomeMatrixJsonConfig(biomeId, map);
    }

    private static Float asFloat(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        return element.getAsFloat();
    }
}
