package com.holybuckets.orecluster.config.model;

import com.holybuckets.foundation.config.ConfigBase;
import com.holybuckets.foundation.config.ConfigModelBase;
import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.orecluster.LoggerProject;
import net.minecraft.world.level.block.Block;
import org.antlr.v4.runtime.misc.Triple;

//Java
import java.util.*;
import java.util.stream.Collectors;

import com.holybuckets.orecluster.config.COreClusters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OreClusterConfigModel extends ConfigModelBase {

    public static final String CLASS_ID = "004";

    public Long subSeed = null;
    public Block oreClusterType = null;
    public HashSet<Block> validOreClusterOreBlocks; //defaultConfigOnly
    public Integer oreClusterSpawnRate = COreClusters.DEF_ORE_CLUSTER_SPAWN_RATE;
    public TripleInt oreClusterVolume = processVolume( COreClusters.DEF_ORE_CLUSTER_VOLUME);
    public Float oreClusterDensity = COreClusters.DEF_ORE_CLUSTER_DENSITY;
    public String oreClusterShape = COreClusters.DEF_ORE_CLUSTER_SHAPE;
    public Integer oreClusterMaxYLevelSpawn = COreClusters.ORE_CLUSTER_MAX_Y_LEVEL_SPAWN;
    public Integer minChunksBetweenOreClusters = COreClusters.MIN_CHUNKS_BETWEEN_ORE_CLUSTERS;
    public Integer maxChunksBetweenOreClusters = COreClusters.MAX_CHUNKS_BETWEEN_ORE_CLUSTERS;
    public Float oreVeinModifier = COreClusters.DEF_ORE_VEIN_MODIFIER;
    public HashSet<Block> oreClusterNonReplaceableBlocks = processStringIntoBlockHashSet(COreClusters.ORE_CLUSTER_NONREPLACEABLE_BLOCKS);
    public HashSet<Block> oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(COreClusters.ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS);
    public Boolean oreClusterDoesRegenerate = COreClusters.REGENERATE_ORE_CLUSTERS;
    public Map<String, Integer> oreClusterRegenPeriods; //defaultConfigOnly

    private static final Gson gson = new GsonBuilder().create();
    private static final COreClusters oreClusterDefaultConfigs = new COreClusters(); //Used for default values

    /**
        Creates a cluster for the given type of block with the default settings
     */
    public OreClusterConfigModel(Block oreClusterBlock ) {
        this.oreClusterType = oreClusterBlock;
    }

    public OreClusterConfigModel(String oreClusterJson) {
        deserialize(oreClusterJson);
    }

    public OreClusterConfigModel( COreClusters cOreClusters )
    {

        if( cOreClusters == null ) {
            return;
        }
        if( cOreClusters.subSeed.get() != null || !cOreClusters.subSeed.get().isEmpty() )
            this.subSeed = (long) cOreClusters.subSeed.get().hashCode();  //initialized to null
        else
            this.subSeed = null;

        this.validOreClusterOreBlocks = new HashSet<Block>(
            processValidOreClusterOreBlocks(cOreClusters.validOreClusterOreBlocks.get()));
        this.oreClusterSpawnRate = cOreClusters.defaultOreClusterSpawnRate.get();
        this.oreClusterVolume = processVolume(cOreClusters.defaultOreClusterVolume.get());
        this.oreClusterDensity = cOreClusters.defaultOreClusterDensity.getF();
        this.oreClusterShape = cOreClusters.defaultOreClusterShape.get();
        this.oreClusterMaxYLevelSpawn = cOreClusters.oreClusterMaxYLevelSpawn.get();
        this.minChunksBetweenOreClusters = cOreClusters.minChunksBetweenOreClusters.get();
        //this.maxChunksBetweenOreClusters = cOreClusters.maxChunksBetweenOreClusters.get();
        this.oreVeinModifier = cOreClusters.defaultOreVeinModifier.getF();
        this.oreClusterNonReplaceableBlocks = processStringIntoBlockHashSet(cOreClusters.defaultOreClusterNonReplaceableBlocks.get());
        this.oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(cOreClusters.defaultOreClusterReplaceableEmptyBlocks.get());
        this.oreClusterDoesRegenerate = cOreClusters.regenerateOreClusters.get();

        //Iterate through the oreClusterRegenPeriods and add them to the map
        oreClusterRegenPeriods = new HashMap<>();
        this.oreClusterRegenPeriods = processRegenPeriods(
            cOreClusters.regenerateOreClusterUpgradeItems.get().split(","),
            cOreClusters.regenerateOreClusterPeriodLengths.get().split(","));

        }
    //END CONSTRUCTOR


    public static List<Block> processValidOreClusterOreBlocks(String validOreClusterOreBlocks) {
        List<String> ores = Arrays.asList(validOreClusterOreBlocks.split(","));
        return ores.stream().map(BlockUtil::blockNameToBlock).collect(Collectors.toList());
    }

    //Setup static methods to process oreClusterReplaceableBlocks and oreClusterReplaceableEmptyBlock
    public static HashSet<Block> processStringIntoBlockHashSet(String replaceableBlocks) {

        return Arrays.stream(replaceableBlocks.split(",")) //Split the string by commas
                .map(BlockUtil::blockNameToBlock)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static HashSet<Block> processReplaceableEmptyBlocks(String replaceableBlocks) {
        HashSet<Block> blocks = processStringIntoBlockHashSet(replaceableBlocks);
        LoggerProject.logDebug("004000", "Blocks: " + blocks);
        if( blocks == null )
            blocks = new HashSet<>();

        if( blocks.isEmpty() || blocks.contains(null))
            blocks.remove(null);

        if( blocks.isEmpty() )
            blocks.add(BlockUtil.blockNameToBlock("minecraft:air"));

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

        String[] mins = COreClusters.MIN_ORE_CLUSTER_VOLUME.split("x");
        String[] maxs = COreClusters.MAX_ORE_CLUSTER_VOLUME.split("x");

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

    public HashMap<String, Integer> processRegenPeriods(String [] upgrades, String [] oreClusterRegenPeriodArray)
    {
        String numberFormatError = "Error parsing oreClusterRegenPeriods, use comma separated list of integers with no spaces";


        HashMap<String, Integer> oreClusterRegenPeriods = new HashMap<>();

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
            oreClusterRegenPeriods = new HashMap<>();
            upgrades = COreClusters.REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS.split(",");
            oreClusterRegenPeriodArray = COreClusters.REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS.split(",");
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

     public void setOreClusterType(Block oreClusterType) {
        this.oreClusterType = oreClusterType;
     }

    public void setOreClusterType(String oreClusterTypeString) {
        this.oreClusterType = BlockUtil.blockNameToBlock(oreClusterTypeString);
    }

    public void setOreClusterSpawnRate(Integer oreClusterSpawnRate) {
        Boolean validConfig = validateInteger(oreClusterSpawnRate, oreClusterDefaultConfigs.defaultOreClusterSpawnRate,
        "for ore: " + this.oreClusterType);

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

        if( !COreClusters.ORE_CLUSTER_VALID_SHAPES.contains( oreClusterShape ) ) {
            oreClusterShape = COreClusters.DEF_ORE_CLUSTER_SHAPE;
            logPropertyWarning(error, this.oreClusterType, null, oreClusterShape);
        }

        this.oreClusterShape = oreClusterShape;
    }

    public void setOreClusterDensity(Float oreClusterDensity)
    {
        Boolean validConfig = validateFloat(oreClusterDensity, oreClusterDefaultConfigs.defaultOreClusterDensity,
         "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreClusterDensity = oreClusterDensity;

    }

    public void setOreClusterMaxYLevelSpawn(Integer oreClusterMaxYLevelSpawn) {
        Boolean validConfig = validateInteger(oreClusterMaxYLevelSpawn, oreClusterDefaultConfigs.oreClusterMaxYLevelSpawn,
        "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreClusterMaxYLevelSpawn = oreClusterMaxYLevelSpawn;
    }

    public void setMinChunksBetweenOreClusters(Integer minChunksBetweenOreClusters)
    {
        String minChunksLogicError = "minChunksBetweenOreClusters is too high for the spawnrate of the cluster";

        Boolean validConfig = validateInteger(minChunksBetweenOreClusters, oreClusterDefaultConfigs.minChunksBetweenOreClusters,
        "for ore: " + this.oreClusterType);

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
                this.oreClusterSpawnRate = (int) (COreClusters.DEF_ORE_CLUSTER_SPAWN_RATE / mandatoryReservedAreaPerCluster);
                logPropertyWarning(minChunksLogicError, this.oreClusterType,
                "scaling down oreClusterSpawnrate to ", this.oreClusterSpawnRate.toString());
            }

            this.minChunksBetweenOreClusters = minChunksBetweenOreClusters;
        }

    }


    public void setMaxChunksBetweenOreClusters(Integer maxChunksBetweenOreClusters)
    {
        String maxChunksLogicError = "maxChunksBetweenOreClusters is too low for the spawnrate of the cluster ";
        ConfigBase.ConfigInt config = oreClusterDefaultConfigs.maxChunksBetweenOreClusters;

        if( config.isDisabled() )
            return;

        Boolean validConfig = validateInteger(maxChunksBetweenOreClusters, config,
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

    public void setOreVeinModifier(Float oreVeinModifier) {
        Boolean validConfig = validateFloat(oreVeinModifier, oreClusterDefaultConfigs.defaultOreVeinModifier,
        "for ore: " + this.oreClusterType);

        if( validConfig )
            this.oreVeinModifier = oreVeinModifier;
    }

    public void setOreClusterNonReplaceableBlocks(String oreClusterNonReplaceableBlocks) {
        this.oreClusterNonReplaceableBlocks = processStringIntoBlockHashSet(oreClusterNonReplaceableBlocks);
    }

    public void setOreClusterReplaceableEmptyBlocks(String oreClusterReplaceableEmptyBlocks) {
        this.oreClusterReplaceableEmptyBlocks = processReplaceableEmptyBlocks(oreClusterReplaceableEmptyBlocks);
    }

    public void setOreClusterDoesRegenerate(String oreClusterDoesRegenerate) {
        this.oreClusterDoesRegenerate = parseBoolean(oreClusterDoesRegenerate);
    }


    private static void logPropertyWarning(String message, Block ore, String defaultMessage, String defaultValue)
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

    public static String serialize( OreClusterConfigModel c )
    {
        JsonObject jsonObject = new JsonObject();
        String oreClusterTypeString = BlockUtil.blockToString(c.oreClusterType);
        jsonObject.addProperty("oreClusterType", oreClusterTypeString);
        jsonObject.addProperty("oreClusterSpawnRate", c.oreClusterSpawnRate);
        jsonObject.addProperty("oreClusterVolume", c.oreClusterVolume.x
                + "x" + c.oreClusterVolume.y
                + "x" + c.oreClusterVolume.z
        );
        jsonObject.addProperty("oreClusterDensity", c.oreClusterDensity);
        jsonObject.addProperty("oreClusterShape", c.oreClusterShape);
        jsonObject.addProperty("oreClusterMaxYLevelSpawn", c.oreClusterMaxYLevelSpawn);
        jsonObject.addProperty("minChunksBetweenOreClusters", c.minChunksBetweenOreClusters);
        //jsonObject.addProperty("maxChunksBetweenOreClusters", maxChunksBetweenOreClusters);

        jsonObject.addProperty("oreVeinModifier", c.oreVeinModifier);
        jsonObject.addProperty("oreClusterNonReplaceableBlocks",
            c.oreClusterNonReplaceableBlocks.stream().map(BlockUtil::blockToString).collect(Collectors.joining(", ")));
        jsonObject.addProperty("oreClusterReplaceableEmptyBlocks",
            c.oreClusterReplaceableEmptyBlocks.stream().map(BlockUtil::blockToString).collect(Collectors.joining(", ")));
        jsonObject.addProperty("oreClusterDoesRegenerate", c.oreClusterDoesRegenerate);

        //System.err.println("jsonObject: " + jsonObject);
        return gson.toJson(jsonObject);
                //replace("\",", "\"," + System.getProperty("line.separator") ).
                        //replace('"', "'".toCharArray()[0]);
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
            LoggerProject.logError("004003", "Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterSpawnRate.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterVolume(jsonObject.get("oreClusterVolume").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004004","Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterVolume.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterDensity(jsonObject.get("oreClusterDensity").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError("004005","Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterDensity.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterShape(jsonObject.get("oreClusterShape").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004006","Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterShape.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterMaxYLevelSpawn(jsonObject.get("oreClusterMaxYLevelSpawn").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004007","Error parsing " +
            oreClusterDefaultConfigs.oreClusterMaxYLevelSpawn.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setMinChunksBetweenOreClusters(jsonObject.get("minChunksBetweenOreClusters").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004008","Error parsing " +
            oreClusterDefaultConfigs.minChunksBetweenOreClusters.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            //setMaxChunksBetweenOreClusters(jsonObject.get("maxChunksBetweenOreClusters").getAsInt());
        } catch (Exception e) {
            LoggerProject.logError("004009","Error parsing " +
            oreClusterDefaultConfigs.maxChunksBetweenOreClusters.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreVeinModifier(jsonObject.get("oreVeinModifier").getAsFloat());
        } catch (Exception e) {
            LoggerProject.logError("004010", "Error parsing " +
            oreClusterDefaultConfigs.defaultOreVeinModifier.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterNonReplaceableBlocks(jsonObject.get("oreClusterNonReplaceableBlocks").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004011", "Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterNonReplaceableBlocks.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterReplaceableEmptyBlocks(jsonObject.get("oreClusterReplaceableEmptyBlocks").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004012", "Error parsing " +
            oreClusterDefaultConfigs.defaultOreClusterReplaceableEmptyBlocks.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
        }

        try {
            setOreClusterDoesRegenerate(jsonObject.get("oreClusterDoesRegenerate").getAsString());
        } catch (Exception e) {
            LoggerProject.logError("004013", "Error parsing " +
            oreClusterDefaultConfigs.regenerateOreClusters.getName() + " for ore: " + this.oreClusterType + ". " + e.getMessage());
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

