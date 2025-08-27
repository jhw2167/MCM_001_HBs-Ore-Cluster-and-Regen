package com.holybuckets.orecluster.config.model;

import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.foundation.block.ModBlocks;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import net.minecraft.world.level.block.Block;

//Java
import java.util.*;
import java.util.stream.Collectors;

import com.holybuckets.orecluster.config.OreClusterConfigData.COreClusters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.state.BlockState;

public class OreClusterConfigModel {

    public static final String CLASS_ID = "004";
    public static Short ID_COUNTER = 0;

    public Long subSeed = null;
    public String configId;
    public BlockState oreClusterType = null;
    public HashSet<Block> validOreClusterOreBlocks; //defaultConfigOnly
    public String oreClusterBiome = ""; // wildcard for all biomes
    public String oreClusterDimensionId = "minecraft:overworld";
    public Integer oreClusterSpawnRate = COreClusters.DEF_ORE_CLUSTER_SPAWN_RATE.get();
    public TripleInt oreClusterVolume = processVolume( COreClusters.DEF_ORE_CLUSTER_VOLUME);
    public Float oreClusterDensity = COreClusters.DEF_ORE_CLUSTER_DENSITY.get();
    public String oreClusterShape = COreClusters.DEF_ORE_CLUSTER_SHAPE;
    public Integer oreClusterMaxYLevelSpawn = COreClusters.DEF_ORE_CLUSTER_MAX_Y_LEVEL_SPAWN.get();
    public Integer oreClusterMinYLevelSpawn = COreClusters.DEF_ORE_CLUSTER_MIN_Y_LEVEL_SPAWN.get();
    public Integer minChunksBetweenOreClusters = COreClusters.DEF_MIN_CHUNKS_BETWEEN_ORE_CLUSTERS.get();
    public Integer maxChunksBetweenOreClusters = COreClusters.DEF_MAX_CHUNKS_BETWEEN_ORE_CLUSTERS.get();
    public Float oreVeinModifier = COreClusters.DEF_ORE_VEIN_MODIFIER.get();
    public HashSet<BlockState> oreClusterNonReplaceableBlocks = processStringIntoBlockStateList(COreClusters.DEF_ORE_CLUSTER_NONREPLACEABLE_BLOCKS)
        .stream().collect(Collectors.toCollection(HashSet::new));
    public List<BlockState> oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(COreClusters.DEF_ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS);
    public Boolean oreClusterDoesRegenerate = COreClusters.DEF_REGENERATE_ORE_CLUSTERS;
    public LinkedHashMap<String, Integer> oreClusterRegenPeriods; //defaultConfigOnly

    private static final Gson gson = new GsonBuilder().create();
    private static final COreClusters oreClusterDefaultConfigs = new COreClusters(); //Used for default values

    /**
        Creates a cluster for the given type of block with the default settings
     */
    public OreClusterConfigModel(BlockState oreClusterBlockState ) {

        //ID is a 4 digit 0 buffered number converted to a string
        this.oreClusterType = oreClusterBlockState;
    }

    public OreClusterConfigModel(String oreClusterJson) {
        this((BlockState) null);
        deserialize(oreClusterJson);
    }

    public OreClusterConfigModel( OreClusterConfigData.COreClusters cOreClusters )
    {
        this((BlockState) null);
        if( cOreClusters == null ) {
            return;
        }
        if( cOreClusters.subSeed != null && !cOreClusters.subSeed.isEmpty() )
            this.subSeed = (long) cOreClusters.subSeed.hashCode();
        else
            this.subSeed = null;

        this.validOreClusterOreBlocks = new HashSet<Block>(
            processValidOreClusterOreBlocks(cOreClusters.validOreClusterOreBlocks));
        this.oreClusterSpawnRate = cOreClusters.oreClusterSpawnRate;
        this.oreClusterDimensionId = cOreClusters.oreClusterDimensionId;
        this.oreClusterVolume = processVolume(cOreClusters.oreClusterVolume);
        this.oreClusterDensity = cOreClusters.oreClusterDensity;
        this.oreClusterShape = cOreClusters.oreClusterShape;
        this.oreClusterMaxYLevelSpawn = cOreClusters.oreClusterMaxYLevelSpawn;
        this.oreClusterMinYLevelSpawn = cOreClusters.oreClusterMinYLevelSpawn;
        this.minChunksBetweenOreClusters = cOreClusters.minChunksBetweenOreClusters;
        //this.maxChunksBetweenOreClusters = cOreClusters.maxChunksBetweenOreClusters;
        this.oreVeinModifier = cOreClusters.oreVeinModifier;
        this.oreClusterNonReplaceableBlocks = processStringIntoBlockStateList(cOreClusters.oreClusterNonreplaceableBlocks )
            .stream().collect(Collectors.toCollection(HashSet::new));
        this.oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(cOreClusters.oreClusterReplaceableEmptyBlocks);
        this.oreClusterDoesRegenerate = cOreClusters.regenerateOreClusters;

        //Iterate through the oreClusterRegenPeriods and add them to the map
        oreClusterRegenPeriods = new LinkedHashMap<>();
        this.oreClusterRegenPeriods = processRegenPeriods(
            cOreClusters.regenerateOreClusterUpgradeItems.split(","),
            cOreClusters.regenerateOreClusterPeriodLengths.split(","));

        }
    //END CONSTRUCTOR

    private static Block blockNameToBlock(String blockName)
    {
        if( blockName == null || blockName.isEmpty() )
            return ModBlocks.empty;

        if( blockName.contains(":") ) {
            String[] split = blockName.split(":");
            return BlockUtil.blockNameToBlock(split[0], split[1]);
        }
        else {
            return BlockUtil.blockNameToBlock("minecraft", blockName);
        }

    }

    public static List<Block> processValidOreClusterOreBlocks(String validOreClusterOreBlocks) {
        List<String> ores = Arrays.asList(validOreClusterOreBlocks.split(","));
        return ores.stream().map(OreClusterConfigModel::blockNameToBlock).collect(Collectors.toList());
    }

    //Setup static methods to process oreClusterReplaceableBlocks and oreClusterReplaceableEmptyBlock
    public static List<BlockState> processStringIntoBlockStateList(String replaceableBlocks) {

        List<Block> blocks = Arrays.stream(replaceableBlocks.split(",")) //Split the string by commas
                .map(OreClusterConfigModel::blockNameToBlock)
                .collect(Collectors.toList());

        //map blocks to defaultBlockState
        return blocks.stream()
                .map(Block::defaultBlockState)
                .collect(Collectors.toList());
    }

    public static List<BlockState> processReplaceableEmptyBlocks(String replaceableBlocks) {
        List<BlockState> blocks = processStringIntoBlockStateList(replaceableBlocks);
        LoggerProject.logDebug("004000", "Blocks: " + blocks);
        if( blocks == null )
            blocks = new ArrayList<>();

        if( blocks.isEmpty() || blocks.contains(null))
            blocks.remove(null);

        if( blocks.isEmpty() )
            blocks.add( blockNameToBlock(Constants.MOD_IDS.FOUNDATION + ":empty_block").defaultBlockState());

        return blocks;
    }


    public TripleInt processVolume(String volume)
    {
        /** Define Errors for validation **/
        StringBuilder volumeNotParsedCorrectlyError = new StringBuilder();
        volumeNotParsedCorrectlyError.append("Volume value: ");
        volumeNotParsedCorrectlyError.append(volume);
        volumeNotParsedCorrectlyError.append(" is not formatted correctly ");

        StringBuilder volumeNotWithinBoundsError = new StringBuilder();
        volumeNotWithinBoundsError.append("Volume value: ");
        volumeNotWithinBoundsError.append(volume);
        volumeNotWithinBoundsError.append(" is out of bounds ");

        /********************************/


        String[] volumeArray = volume.toLowerCase().split("x");
        if(volume == null || volume.isEmpty() || volumeArray.length != 3) {
            volumeArray = COreClusters.DEF_ORE_CLUSTER_VOLUME.split("x");
            logPropertyWarning(volumeNotParsedCorrectlyError.toString(), this.oreClusterType, null, volumeArray.toString() );
        }

        String[] mins = COreClusters.DEF_MIN_ORE_CLUSTER_VOLUME.split("x");
        String[] maxs = COreClusters.DEF_MAX_ORE_CLUSTER_VOLUME.split("x");

        //Validate we are within MIN and MAX
        for (int i = 0; i < 3; i++) {
            int vol = Integer.parseInt(volumeArray[i]);
            int min = Integer.parseInt(mins[i]);
            int max = Integer.parseInt(maxs[i]);
            if (vol < min || vol > max)
            {
                volumeArray = COreClusters.DEF_ORE_CLUSTER_VOLUME.split("x");
                logPropertyWarning(volumeNotWithinBoundsError.toString(), this.oreClusterType, null, volumeArray.toString() );
                break;
            }
        }

        return new TripleInt(Integer.parseInt(volumeArray[0]),
            Integer.parseInt(volumeArray[1]),
            Integer.parseInt(volumeArray[2]));
    }

    public LinkedHashMap<String, Integer> processRegenPeriods(String [] upgrades, String [] oreClusterRegenPeriodArray)
    {
        String numberFormatError = "Error parsing oreClusterRegenPeriods, use comma separated list of integers with no spaces";


        LinkedHashMap<String, Integer> oreClusterRegenPeriods = new LinkedHashMap<>();

        int i = 0;
        try {
            for (String item : upgrades) {
                //before putting into map,check if there is a valid corresponding length
                if (i < oreClusterRegenPeriodArray.length) {
                    oreClusterRegenPeriods.put(item, Integer.parseInt(oreClusterRegenPeriodArray[i].trim()));
                    i++;
                } else {
                    //If there is no corresponding length, use last number we got
                    oreClusterRegenPeriods.put(item, Integer.parseInt(oreClusterRegenPeriodArray[i].trim()));
                }

            }
        }
        catch (NumberFormatException e) {
            //Reset map to default values given error
            oreClusterRegenPeriods = new LinkedHashMap<>();
            upgrades = COreClusters.DEF_REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS.split(",");
            oreClusterRegenPeriodArray = COreClusters.DEF_REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS.split(",");
            i = 0;
            for (String item : upgrades) {
                oreClusterRegenPeriods.put(item, Integer.parseInt(oreClusterRegenPeriodArray[i]));
                i++;
            }

            logPropertyWarning(numberFormatError, this.oreClusterType, null, oreClusterRegenPeriods.toString() );
        }
        return oreClusterRegenPeriods;
    }



    /*
        @javadoc
        Setter Functions
     */

     public void setConfigId() {
        if(this.oreClusterType == null ) ID_COUNTER = 0;
         this.configId = String.format("%04d", ID_COUNTER++);
     }

     public void setOreClusterType(BlockState oreClusterType) {
        this.oreClusterType = oreClusterType;
     }

    public void setOreClusterType(String oreClusterTypeString) {
        this.oreClusterType = this.blockNameToBlock(oreClusterTypeString).defaultBlockState();
    }

    public void setOreClusterSpawnRate(Integer oreClusterSpawnRate) {
        Boolean validConfig = Validator.validateNumber( oreClusterSpawnRate,
            oreClusterDefaultConfigs.DEF_ORE_CLUSTER_SPAWN_RATE,
            "for ore: " + this.oreClusterType );

        if( validConfig )
            this.oreClusterSpawnRate = oreClusterSpawnRate;
    }

    public void setOreClusterVolume(String oreClusterVolume) {
        TripleInt volume = processVolume(oreClusterVolume);
        this.oreClusterVolume = volume;
    }

    public void setOreClusterShape(String oreClusterShape)
    {
        String error = "Error setting oreClusterShape for ore: ";

        if( oreClusterShape == null || oreClusterShape.isEmpty() )
            oreClusterShape = COreClusters.DEF_ORE_CLUSTER_SHAPE;

        if( !COreClusters.DEF_ORE_CLUSTER_VALID_SHAPES.contains( oreClusterShape ) ) {
            oreClusterShape = COreClusters.DEF_ORE_CLUSTER_SHAPE;
            logPropertyWarning(error, this.oreClusterType, null, oreClusterShape);
        }

        this.oreClusterShape = oreClusterShape;
    }

    public void setOreClusterDensity(Float oreClusterDensity)
    {
        Boolean validConfig = Validator.validateNumber(oreClusterDensity,
            oreClusterDefaultConfigs.DEF_ORE_CLUSTER_DENSITY,
            "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreClusterDensity = oreClusterDensity;

    }

    public void setOreClusterMaxYLevelSpawn(Integer oreClusterMaxYLevelSpawn)
    {
        Boolean validConfig = Validator.validateNumber(oreClusterMaxYLevelSpawn,
            oreClusterDefaultConfigs.DEF_ORE_CLUSTER_MAX_Y_LEVEL_SPAWN,
            "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreClusterMaxYLevelSpawn = oreClusterMaxYLevelSpawn;
    }

    public void setOreClusterMinYLevelSpawn(Integer oreClusterMinYLevelSpawn)
    {
        Boolean validConfig = Validator.validateNumber(oreClusterMinYLevelSpawn,
            oreClusterDefaultConfigs.DEF_ORE_CLUSTER_MIN_Y_LEVEL_SPAWN,
            "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreClusterMinYLevelSpawn = oreClusterMinYLevelSpawn;
    }

    public void setMinChunksBetweenOreClusters(Integer minChunksBetweenOreClusters)
    {
        String minChunksLogicError = "minChunksBetweenOreClusters is too high for the spawnrate of the cluster";

        Boolean validConfig = Validator.validateNumber( minChunksBetweenOreClusters,
            oreClusterDefaultConfigs.DEF_MIN_CHUNKS_BETWEEN_ORE_CLUSTERS,
            "for ore: " + this.oreClusterType
        );

        if( validConfig )
        {
            //Validate there is enough cluster space to meet expected chunks per cluster
            double mandatoryReservedAreaPerCluster = Math.pow( 2*minChunksBetweenOreClusters + 1, 2);
            double expectedAreaPerCluster = (COreClusters.DEF_ORE_CLUSTER_SPAWNRATE_AREA / oreClusterSpawnRate);

            /** If the expected area reserved per cluster (given an even distribution),
             *  is less than the mandatory area given the spacing, our spawnrate is
             *  too high to meet the spacing requirements on average;
             *  we will reduce the spawnrate to meet the expected area
             */
            if ( expectedAreaPerCluster < mandatoryReservedAreaPerCluster  )
            {
                int defaultSpawnRate = COreClusters.DEF_ORE_CLUSTER_SPAWN_RATE.get();
                this.oreClusterSpawnRate = (int) ( defaultSpawnRate / mandatoryReservedAreaPerCluster);
                logPropertyWarning(minChunksLogicError, this.oreClusterType,
                "scaling down oreClusterSpawnrate to ", this.oreClusterSpawnRate.toString());
            }

            this.minChunksBetweenOreClusters = minChunksBetweenOreClusters;
        }

    }


    public void setMaxChunksBetweenOreClusters(Integer maxChunksBetweenOreClusters)
    {
        String maxChunksLogicError = "maxChunksBetweenOreClusters is too low for the spawnrate of the cluster ";

        Boolean validConfig = Validator.validateNumber(maxChunksBetweenOreClusters,
        oreClusterDefaultConfigs.DEF_MAX_CHUNKS_BETWEEN_ORE_CLUSTERS,
        "for ore: " + this.oreClusterType);

        if( validConfig )
        {
            //Validate there is enough cluster space to meet expected chunks per cluster
            double minimumClustersPerArea = COreClusters.DEF_ORE_CLUSTER_SPAWNRATE_AREA /
                 Math.pow( 2*maxChunksBetweenOreClusters + 1, 2) ;

            if ( ( this.oreClusterSpawnRate / 2 ) < minimumClustersPerArea )
            {
                this.oreClusterSpawnRate = (int) minimumClustersPerArea * 2;
                logPropertyWarning(maxChunksLogicError, this.oreClusterType,
                "scaling up oreClusterSpawnrate to ", this.oreClusterSpawnRate.toString());
            }

            this.maxChunksBetweenOreClusters = maxChunksBetweenOreClusters;

        }

    }

    public void setOreVeinModifier(Float oreVeinModifier)
    {
        Boolean validConfig = Validator.validateNumber(oreVeinModifier,
            oreClusterDefaultConfigs.DEF_ORE_VEIN_MODIFIER,
            "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreVeinModifier = oreVeinModifier;
    }

    public void setOreClusterNonReplaceableBlocks(String oreClusterNonReplaceableBlocks) {
        this.oreClusterNonReplaceableBlocks = processStringIntoBlockStateList(oreClusterNonReplaceableBlocks)
            .stream().collect(Collectors.toCollection(HashSet::new));
    }

    public void setOreClusterReplaceableEmptyBlocks(String oreClusterReplaceableEmptyBlocks) {
        this.oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(oreClusterReplaceableEmptyBlocks);
    }

    public void setOreClusterDoesRegenerate(String oreClusterDoesRegenerate) {
        this.oreClusterDoesRegenerate = Validator.parseBoolean(oreClusterDoesRegenerate);
    }

    public void setOreClusterDimensionId(String oreClusterDimensionId) {
        if (oreClusterDimensionId == null || oreClusterDimensionId.isEmpty()) {
            this.oreClusterDimensionId = COreClusters.DEF_ORE_CLUSTER_DIMENSION;
            logPropertyWarning("Invalid dimension", this.oreClusterType, null, this.oreClusterDimensionId);
        } else {
            this.oreClusterDimensionId = oreClusterDimensionId;
        }
    }

    public void setOreClusterBiome(String oreClusterBiome) {
        if (oreClusterBiome == null || oreClusterBiome.isEmpty()) {
            this.oreClusterBiome = COreClusters.DEF_ORE_CLUSTER_BIOME;
            logPropertyWarning("Invalid biome", this.oreClusterType, null, this.oreClusterBiome);
        } else {
            this.oreClusterBiome = oreClusterBiome;
        }
    }


    private static void logPropertyWarning(String message, BlockState ore, String defaultMessage, String defaultValue)
    {
        if( defaultMessage == null )
            defaultMessage = " Using default value of ";

        StringBuilder error = new StringBuilder();
        error.append(message);
        error.append(" for ore: ");
        error.append(ore);
        error.append( defaultMessage );
        error.append(defaultValue);
        LoggerProject.logWarning("004001", error.toString());
    }


    /*
        @javadoc
        Serialize and Deserialize the config for specific ores from JSON strings

      *************
     */
    public String serialize() {
        return serialize(this);
    }

    public JsonObject serializeJson() {
        return serializeJson(this);
    }

    public static String serialize( OreClusterConfigModel c ) {
        return gson.toJson(serializeJson(c));
    }

    public static JsonObject serializeJson( OreClusterConfigModel c )
    {
        JsonObject jsonObject = new JsonObject();
        String oreClusterTypeString = BlockUtil.blockToString(c.oreClusterType.getBlock());
        jsonObject.addProperty("oreClusterType", oreClusterTypeString);
        jsonObject.addProperty("oreClusterSpawnRate", c.oreClusterSpawnRate);
        jsonObject.addProperty("oreClusterDimension", c.oreClusterDimensionId);
        jsonObject.addProperty("oreClusterBiome", c.oreClusterBiome);
        jsonObject.addProperty("oreClusterVolume", c.oreClusterVolume.x
                + "x" + c.oreClusterVolume.y
                + "x" + c.oreClusterVolume.z
        );
        jsonObject.addProperty("oreClusterDensity", c.oreClusterDensity);
        jsonObject.addProperty("oreClusterShape", c.oreClusterShape);
        jsonObject.addProperty("oreClusterMaxYLevelSpawn", c.oreClusterMaxYLevelSpawn);
        jsonObject.addProperty("oreClusterMinYLevelSpawn", c.oreClusterMinYLevelSpawn);
        jsonObject.addProperty("minChunksBetweenOreClusters", c.minChunksBetweenOreClusters);
        //jsonObject.addProperty("maxChunksBetweenOreClusters", maxChunksBetweenOreClusters);

        jsonObject.addProperty("oreVeinModifier", c.oreVeinModifier);
        jsonObject.addProperty("oreClusterNonReplaceableBlocks",
            c.oreClusterNonReplaceableBlocks.stream().map(bs -> bs.getBlock()).map(BlockUtil::blockToString).collect(Collectors.joining(", ")));
        jsonObject.addProperty("oreClusterReplaceableEmptyBlocks",
            c.oreClusterReplaceableEmptyBlocks.stream().map(bs -> bs.getBlock()).map(BlockUtil::blockToString).collect(Collectors.joining(", ")));
        jsonObject.addProperty("oreClusterDoesRegenerate", c.oreClusterDoesRegenerate);

        return jsonObject;
    }

    public void deserialize(String jsonString)
    {
        JsonObject jsonObject = JsonParser.parseString(jsonString.replace("'".toCharArray()[0], '"')).getAsJsonObject();

        try {
            String oreType = jsonObject.get("oreClusterType").getAsString();
            LoggerProject.logDebug("004000", "Deserealizing OreClusterType: " + oreType);
            setOreClusterType(oreType);
        } catch (Exception e) {
            LoggerProject.logError("004002","Error parsing oreClusterType for an undefined ore" + e.getMessage());
        }

        try {
            setOreClusterSpawnRate(jsonObject.get("oreClusterSpawnRate").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004003", "Error parsing oreClusterSpawnRate" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterVolume(jsonObject.get("oreClusterVolume").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004004","Error parsing oreClusterVolume" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterDensity(jsonObject.get("oreClusterDensity").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError("004005","Error parsing oreClusterDensity" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterShape(jsonObject.get("oreClusterShape").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004006","Error parsing oreClusterShape" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterMaxYLevelSpawn(jsonObject.get("oreClusterMaxYLevelSpawn").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004007","Error parsing oreClusterMaxYLevelSpawn" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterMinYLevelSpawn(jsonObject.get("oreClusterMinYLevelSpawn").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004015","Error parsing oreClusterMinYLevelSpawn" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setMinChunksBetweenOreClusters(jsonObject.get("minChunksBetweenOreClusters").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004008","Error parsing minChunksBetweenOreClusters" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            //setMaxChunksBetweenOreClusters(jsonObject.get("maxChunksBetweenOreClusters").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004009","Error parsing maxChunksBetweenOreClusters" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreVeinModifier(jsonObject.get("oreVeinModifier").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError("004010", "Error parsing oreVeinModifier" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterNonReplaceableBlocks(jsonObject.get("oreClusterNonReplaceableBlocks").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004011", "Error parsing oreClusterNonReplaceableBlocks" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterReplaceableEmptyBlocks(jsonObject.get("oreClusterReplaceableEmptyBlocks").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004012", "Error parsing oreClusterReplaceableEmptyBlocks" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterDoesRegenerate(jsonObject.get("oreClusterDoesRegenerate").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004013", "Error parsing oreClusterDoesRegenerate" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterDimensionId(jsonObject.get("oreClusterDimensionId").getAsString());
            setOreClusterBiome(jsonObject.get("oreClusterBiome").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004016", "Error parsing oreClusterDimension or oreClusterBiome" +
            " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        StringBuilder complete = new StringBuilder();
        complete.append("OreClusterConfigModel for ");
        complete.append(this.oreClusterType);
        complete.append(" has been created with the following properties: \n");
        complete.append(serialize(this));
        complete.append("\n\n");
        LoggerProject.logInfo("004014", complete.toString());
    }


}

