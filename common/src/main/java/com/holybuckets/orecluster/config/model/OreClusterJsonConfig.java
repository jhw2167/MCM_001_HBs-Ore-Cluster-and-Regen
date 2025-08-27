package com.holybuckets.orecluster.config.model;


import com.google.gson.*;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.block.ModBlocks;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/*
 *  Represents the JSON configuration values for Mod
 *  - Each datatype is a primitive, List, or JsonObject
 *  - JSON configurations consist of config values that would not fit well into the .toml config file, like
 *  arrays, matrices, and their corresponding default values.
 *  - The chief purpose of this class is to support serializing and deserializing JSON configurations
 *  values should be read into a configModel object when they are accessed at runtime, which is why
 *  the class does not support setters
 */
public class OreClusterJsonConfig implements IStringSerializable
{
    public static class OreClusterId {
        private final int state; // 3 digit numeric ID
        
        public OreClusterId(String level, String biome, String blockName) {
            // Create deterministic hash from combined strings
            String combined = level + "|" + biome + "|" + blockName;
            // Get positive hash and limit to 3 digits
            this.state = Math.abs(combined.hashCode() % 1000);
        }

        public static OreClusterId getId(ResourceLocation level, ResourceLocation biome, ResourceLocation blockName) {
            return new OreClusterId(
                level != null ? level.toString() : "",
                biome != null ? biome.toString() : "",
                blockName != null ? blockName.toString() : ""
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OreClusterId that = (OreClusterId) o;
            return state == that.state;
        }

        @Override
        public int hashCode() {
            return state;
        }
    }

    public static final String CLASS_ID = "006";
    public static final OreClusterJsonConfig DEFAULT_CONFIG = new OreClusterJsonConfig();

    //Lists of complex objects MUST be JsonObject because I don't trust Gson to serialize/deserialize my types
    private List<JsonObject> oreClusterConfigs;


    /* Constructors **/

    /**
     * Private constructor to set default value
     */
    private OreClusterJsonConfig() {
        super();
    }

    public OreClusterJsonConfig(Map<BlockState, OreClusterConfigModel> configs) {
        super();
        initFromMap(configs);
    }

    public OreClusterJsonConfig(String jsonString) {
        super();
        initDefaults();
        deserialize(jsonString);
    }

    /* ################ **/
    /** END CONSTRUCTORS **/
    /** ################ **/

    /** Getters **/

    public List<OreClusterConfigModel> getOreClusterConfigs() {
        List<OreClusterConfigModel> oreClusterConfigModels = new ArrayList<>();

        for (JsonObject clusterConfig : this.oreClusterConfigs)
        {
            try {
                oreClusterConfigModels.add( new OreClusterConfigModel(clusterConfig.toString()) );
            } catch (Exception e) {
                LoggerProject.logError("006000", "Error getting OreClusterConfigs: " + e.getMessage());
            }
        }

        return oreClusterConfigModels;
    }

    private void initFromMap(Map<BlockState, OreClusterConfigModel> configs) {
        this.oreClusterConfigs = new ArrayList<>();
        for (OreClusterConfigModel model : configs.values()) {
            this.oreClusterConfigs.add( model.serializeJson() );
        }
    }

    private static BlockState bs(Block b) {
        return b.defaultBlockState();
    }

    private final static Set<BlockState> baseListNonReplaceable = Set.of(
    bs(Blocks.END_PORTAL_FRAME),
    bs(Blocks.BEDROCK)
    );

    private void initDefaults()
    {
        final OreClusterConfigModel IRON = new OreClusterConfigModel(Blocks.IRON_ORE.defaultBlockState());
            IRON.oreClusterSpawnRate = 8;
            IRON.oreClusterVolume = new HBUtil.TripleInt(16, 12, 16);
            IRON.oreClusterDensity = 0.25f;
            IRON.oreClusterMaxYLevelSpawn = 64;
            IRON.oreClusterReplaceableEmptyBlocks = new ArrayList<>();
            IRON.oreClusterNonReplaceableBlocks = new HashSet<>(baseListNonReplaceable);
                IRON.oreClusterNonReplaceableBlocks.add(bs(Blocks.AIR));
                IRON.oreClusterNonReplaceableBlocks.add(bs(Blocks.DIRT));
                IRON.oreClusterNonReplaceableBlocks.add(bs(Blocks.GRASS));

        final OreClusterConfigModel COAL = new OreClusterConfigModel(Blocks.COAL_ORE.defaultBlockState());
            COAL.oreClusterSpawnRate = 32;
            COAL.oreClusterVolume = new HBUtil.TripleInt(8, 8, 8);
            COAL.oreClusterShape = "SPHERE";
            COAL.oreClusterDensity = 0.4f;
            COAL.oreClusterMaxYLevelSpawn = 64;
            COAL.oreClusterReplaceableEmptyBlocks = List.of(
                bs(Blocks.STONE),
                bs(Blocks.STONE),
                bs(Blocks.AIR)
            );
            COAL.oreClusterDoesRegenerate = false;

        COAL.oreClusterNonReplaceableBlocks = new HashSet<>(baseListNonReplaceable);
            COAL.oreClusterNonReplaceableBlocks.add(bs(Blocks.AIR));
            COAL.oreClusterNonReplaceableBlocks.add(bs(Blocks.DIRT));
            COAL.oreClusterNonReplaceableBlocks.add(bs(Blocks.GRASS));

        final OreClusterConfigModel DPSLT_DIAMOND = new OreClusterConfigModel(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
            DPSLT_DIAMOND.oreClusterSpawnRate = 2;
            DPSLT_DIAMOND.oreClusterVolume = new HBUtil.TripleInt(5, 4, 5);
            DPSLT_DIAMOND.oreClusterDensity = 0.32f;
            DPSLT_DIAMOND.oreClusterMaxYLevelSpawn = -16;
            DPSLT_DIAMOND.oreClusterMinYLevelSpawn = -56;
            DPSLT_DIAMOND.oreClusterReplaceableEmptyBlocks = new ArrayList<>();
            DPSLT_DIAMOND.oreClusterReplaceableEmptyBlocks.add(Blocks.DEEPSLATE.defaultBlockState());
            DPSLT_DIAMOND.oreClusterNonReplaceableBlocks = new HashSet<>(baseListNonReplaceable);
                DPSLT_DIAMOND.oreClusterNonReplaceableBlocks.add(bs(Blocks.AIR));
        final OreClusterConfigModel DPSLT_LAPIS = new OreClusterConfigModel(Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState());
            DPSLT_LAPIS.oreClusterSpawnRate = 10;
            DPSLT_LAPIS.oreClusterShape = "SPHERE";
            DPSLT_LAPIS.oreClusterVolume = new HBUtil.TripleInt(4, 6, 4);
            DPSLT_LAPIS.oreClusterDensity = 0.1f;
            DPSLT_LAPIS.oreClusterMaxYLevelSpawn = 0;
            DPSLT_LAPIS.oreClusterMinYLevelSpawn = -56;
            DPSLT_LAPIS.oreClusterReplaceableEmptyBlocks = List.of(
                bs(Blocks.DEEPSLATE_REDSTONE_ORE),
                bs(Blocks.DEEPSLATE_DIAMOND_ORE),
                bs(Blocks.DEEPSLATE_EMERALD_ORE),
                bs(Blocks.DEEPSLATE),
                bs(Blocks.DEEPSLATE),
                bs(Blocks.DEEPSLATE),
                bs(Blocks.DEEPSLATE),
                bs(Blocks.DEEPSLATE),
                bs(Blocks.DEEPSLATE) //6
            );
            DPSLT_LAPIS.oreClusterDoesRegenerate = false;
            DPSLT_LAPIS.oreClusterNonReplaceableBlocks = new HashSet<>(baseListNonReplaceable);
                DPSLT_LAPIS.oreClusterNonReplaceableBlocks.add(bs(Blocks.AIR));

        this.oreClusterConfigs = new ArrayList<>()
        {{
            add( JsonParser.parseString(OreClusterConfigModel.serialize(IRON)).getAsJsonObject());
            add( JsonParser.parseString(OreClusterConfigModel.serialize(COAL)).getAsJsonObject());
            add( JsonParser.parseString(OreClusterConfigModel.serialize(DPSLT_DIAMOND)).getAsJsonObject());
            add( JsonParser.parseString(OreClusterConfigModel.serialize(DPSLT_LAPIS)).getAsJsonObject());
        }};
    }


    /* ################ **/
    /** END GETTERS **/
    /** ################ **/


    /** Serializing **/

    public String serialize() {
        return new Gson().toJson(this);
    }

    /*
     * Deserialize the JSON string into the OreClusterJsonConfig object
     * - We want to let any complex type serialize itself
     * @param jsonString
     * @return
     */
    public void deserialize(String jsonString)
    {
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();

        try {
            List<JsonObject> oreClusterConfigs = new ArrayList<>();
            if( !jsonObject.has("oreClusterConfigs") )
                return;

            JsonArray clusterConfigs = jsonObject.getAsJsonArray("oreClusterConfigs");

            for (int i = 0; i < clusterConfigs.size(); i++)
            {
                try {
                    oreClusterConfigs.add( clusterConfigs.get(i).getAsJsonObject() );
                } catch (Exception e) {
                    LoggerProject.logError("006001", "Error deserializing OreClusterJsonConfig: " + e.getMessage());
                }
            }
            //END FOR

            this.oreClusterConfigs = oreClusterConfigs;

        } catch (Exception e) {
            LoggerProject.logError("006002", "Error deserializing OreClusterJsonConfig: " + e.getMessage());
        }

    }

}
