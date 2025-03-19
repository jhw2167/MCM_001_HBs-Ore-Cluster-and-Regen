package com.holybuckets.orecluster.config.model;


import com.google.gson.*;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    private void initDefaults()
    {
        final OreClusterConfigModel IRON = new OreClusterConfigModel(Blocks.IRON_ORE.defaultBlockState());
        IRON.oreClusterSpawnRate = 16;
        IRON.oreClusterVolume = new HBUtil.TripleInt(12, 8, 12);
        IRON.oreClusterDensity = 0.1f;
        IRON.oreClusterReplaceableEmptyBlocks = new HashSet<>();
        final OreClusterConfigModel DPSLT_DIAMOND = new OreClusterConfigModel(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
        DPSLT_DIAMOND.oreClusterSpawnRate = 10;
        DPSLT_DIAMOND.oreClusterVolume = new HBUtil.TripleInt(5, 4, 5);
        DPSLT_DIAMOND.oreClusterDensity = 0.75f;
        DPSLT_DIAMOND.oreClusterReplaceableEmptyBlocks = new HashSet<>();
        DPSLT_DIAMOND.oreClusterReplaceableEmptyBlocks.add(Blocks.DEEPSLATE.defaultBlockState());

        this.oreClusterConfigs = new ArrayList<>()
        {{
            add( JsonParser.parseString(OreClusterConfigModel.serialize(IRON)).getAsJsonObject());
            add( JsonParser.parseString(OreClusterConfigModel.serialize(DPSLT_DIAMOND)).getAsJsonObject());
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
            this.oreClusterConfigs = new ArrayList<>();
            if( !jsonObject.has("oreClusterConfigs") )
                return;

            JsonArray clusterConfigs = jsonObject.getAsJsonArray("oreClusterConfigs");

            for (int i = 0; i < clusterConfigs.size(); i++)
            {
                try {
                    this.oreClusterConfigs.add( clusterConfigs.get(i).getAsJsonObject() );
                } catch (Exception e) {
                    LoggerProject.logError("006001", "Error deserializing OreClusterJsonConfig: " + e.getMessage());
                }
            }
            //END FOR


        } catch (Exception e) {
            LoggerProject.logError("006002", "Error deserializing OreClusterJsonConfig: " + e.getMessage());
        }

    }

}
