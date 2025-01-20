package com.holybuckets.orecluster.config;

import com.holybuckets.foundation.config.ConfigBase;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.HashSet;

public class COreClusters extends ConfigBase {

    //Put the defaults into public static final fields
    public static String SUB_SEED = "";
    public static Integer DEF_ORE_CLUSTER_SPAWNRATE_AREA = 256;
    public static final String DEF_VALID_ORE_CLUSTER_ORE_BLOCKS = "minecraft:iron_ore,minecraft:diamond_ore,minecraft:gold_ore,minecraft:coal_ore";
    public static final int DEF_ORE_CLUSTER_SPAWN_RATE = 16;
    public static final String DEF_ORE_CLUSTER_VOLUME = "16x16x16";
    public static final float DEF_ORE_CLUSTER_DENSITY = 0.80f;
    public static final String DEF_ORE_CLUSTER_SHAPE = "any";
    public static final int ORE_CLUSTER_MAX_Y_LEVEL_SPAWN = 256;
    public static final int MIN_CHUNKS_BETWEEN_ORE_CLUSTERS = 0;
    public static final int MAX_CHUNKS_BETWEEN_ORE_CLUSTERS = 9;
    public static final float DEF_ORE_VEIN_MODIFIER = 1f;
    public static final String ORE_CLUSTER_NONREPLACEABLE_BLOCKS = "minecraft:end_portal_frame,minecraft:bedrock";
    public static final String ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS = "minecraft:stone,minecraft:air";

    public static final boolean REGENERATE_ORE_CLUSTERS = true;
    public static final String REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS = "28,24,16,8";
    public static final String REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS = "default,minecraft:blaze_powder," +
     "minecraft:dragon_egg,minecraft:nether_star";
    public static final String ORE_CLUSTER_FILE_CONFIG_PATH = "config/HBOreClustersAndRegenConfigs.json";

     //Ranges
     public static final String MIN_ORE_CLUSTER_VOLUME = "0x0x0";
     public static final String MAX_ORE_CLUSTER_VOLUME = "64x64x64";
     public static final HashSet<String> ORE_CLUSTER_VALID_SHAPES = new HashSet<>(Arrays.asList("bowl", "anvil", "shale", "any"));

    public final ConfigString subSeed = s(SUB_SEED, "subSeed", Comments.DEF_SUB_SEED);
    public final ConfigString validOreClusterOreBlocks = s(DEF_VALID_ORE_CLUSTER_ORE_BLOCKS, "validOreClusterOreBlocks", Comments.DEF_VALID_ORE_CLUSTER_ORE_BLOCKS);
    public final ConfigInt defaultOreClusterSpawnRate = i(DEF_ORE_CLUSTER_SPAWN_RATE, 0, DEF_ORE_CLUSTER_SPAWNRATE_AREA, "defaultOreClusterSpawnRate", Comments.DEF_ORE_CLUSTER_SPAWN_RATE);
    public final ConfigString defaultOreClusterVolume = s(DEF_ORE_CLUSTER_VOLUME, "defaultOreClusterVolume", Comments.DEF_ORE_CLUSTER_VOLUME);
    public final ConfigFloat defaultOreClusterDensity = f(DEF_ORE_CLUSTER_DENSITY, 0, 1, "defaultOreClusterDensity", Comments.DEF_ORE_CLUSTER_DENSITY);
    public final ConfigString defaultOreClusterShape = s(DEF_ORE_CLUSTER_SHAPE, "defaultOreClusterShape", Comments.DEF_ORE_CLUSTER_SHAPE);
    public final ConfigInt oreClusterMaxYLevelSpawn = i(ORE_CLUSTER_MAX_Y_LEVEL_SPAWN, -64, 1024, "oreClusterMaxYLevelSpawn", Comments.ORE_CLUSTER_MAX_Y_LEVEL_SPAWN);
    public final ConfigInt minChunksBetweenOreClusters = i(MIN_CHUNKS_BETWEEN_ORE_CLUSTERS, 0, 16, "minChunksBetweenOreClusters", Comments.MIN_CHUNKS_BETWEEN_ORE_CLUSTERS);
    public final ConfigInt maxChunksBetweenOreClusters = i(MAX_CHUNKS_BETWEEN_ORE_CLUSTERS, 1, 16, "maxChunksBetweenOreClusters", Comments.MAX_CHUNKS_BETWEEN_ORE_CLUSTERS);
    public final ConfigFloat defaultOreVeinModifier = f(DEF_ORE_VEIN_MODIFIER, 0, 1, "defaultOreVeinModifier", Comments.DEF_ORE_VEIN_MODIFIER);
    public final ConfigString defaultOreClusterNonReplaceableBlocks = s(ORE_CLUSTER_NONREPLACEABLE_BLOCKS, "defaultOreClusterNonReplaceableBlocks", Comments.ORE_CLUSTER_NONREPLACEABLE_BLOCKS);
    public final ConfigString defaultOreClusterReplaceableEmptyBlocks = s(ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS, "defaultOreClusterReplaceableEmptyBlock", Comments.ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS);
    public final ConfigBool regenerateOreClusters = b(REGENERATE_ORE_CLUSTERS, "regenerateOreClusters", Comments.REGENERATE_ORE_CLUSTERS);
    public final ConfigString regenerateOreClusterPeriodLengths = s(REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS, "regenerateOreClusterPeriodLengths", Comments.REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS);
    public final ConfigString regenerateOreClusterUpgradeItems = s(REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS, "regenerateOreClusterUpgradeItems", Comments.REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS);

        public final ConfigString oreClusters = s(ORE_CLUSTER_FILE_CONFIG_PATH, "oreClustersJsonConfigFilePath", Comments.ORE_CLUSTERS);
        public static final OreClusterConfigModel defaultIronOreClusterConfig = new OreClusterConfigModel(Blocks.IRON_ORE );
        public static final OreClusterConfigModel defaultDiamondOreClusterConfig = new OreClusterConfigModel(Blocks.DIAMOND_ORE);



    @Override
    public String getName() {
        return "OreClusters";
    }

    public static class Comments {
        public static final String DEF_SUB_SEED = "Sub-seed used to generate random numbers for ore cluster generation - by default," +
         " Ore cluster generation uses the world seed to determine which chunks have ore clusters and their shape and size. By assigning a " +
          "sub-seed, you can adjust this randomness for the same world";
        public static final String DEF_VALID_ORE_CLUSTER_ORE_BLOCKS = "List of blocks that the mod will attempt to create clusters for upon chunk load. " +
         "Clusters created from these blocks will take all default ore cluster parameters unless overridden." +
          " If you are going to override the default parameters for an ore anyways, you don't need to include it in this list";
        public static final String DEF_ORE_CLUSTER_SPAWN_RATE = "Defines the default frequency of ore clusters. Takes an integer" +
         " as the number of expected ore clusters per " + DEF_ORE_CLUSTER_SPAWNRATE_AREA + " chunks";
        public static final String DEF_ORE_CLUSTER_VOLUME = "Specifies the default dimensions of a cluster. <X>x<Y>x<Z>. " +
         "The true cluster will always be smaller than this box because it will choose a shape that roughly fits inside it," +
          " max 64x64x64 else it will revert to the default 16x16x16";
        public static final String DEF_ORE_CLUSTER_DENSITY = "Determines the density of ore within a cluster. To reduce density" +
         " ore blocks within the cluster will be replaced with blocks from 'defaultOreClusterReplaceableEmptyBlock' below";
        public static final String DEF_ORE_CLUSTER_SHAPE = "Defines the shape of the ore cluster. Options are 'bowl', 'anvil', 'shale', 'flat' or 'any'. " +
         "Defaults to any, which takes a random shape";
        public static final String ORE_CLUSTER_MAX_Y_LEVEL_SPAWN = "Maximum Y-level at which clusters can spawn";
        public static final String MIN_CHUNKS_BETWEEN_ORE_CLUSTERS = "Minimum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS"+
            "- this is a rough guideline, the random generation is not perfect";
        public static final String MAX_CHUNKS_BETWEEN_ORE_CLUSTERS = "Maximum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS " +
            "- this is a rough guideline, the random generation is not perfect";
        public static final String DEF_ORE_VEIN_MODIFIER = "Scales the presence of normal (small) ore veins between 0 and 1. This mod " +
         "replaces existing ore veins in real time with the specified first block in 'defaultOreClusterReplaceableEmptyBlock' block so can only" +
          " reduce the frequency of ore veins, not increase it";
        public static final String ORE_CLUSTER_NONREPLACEABLE_BLOCKS = "List of blocks that should not be replaced by the specified ore during cluster generation. " +
         "For example, if you don't want ore clusters to replace bedrock - which is very reasonable - you would add 'minecraft:bedrock' to this list";
        public static final String ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS = "Block used to fill in the ore cluster shape when we want the cluster to be more sparse " +
         "this field can take multiple comma seperated blocks; but only the first block will be used to replace ore veins if ORE_VEIN_MODIFIER is below 1";
        public static final String REGENERATE_ORE_CLUSTERS = "Flag indicating if ore clusters should regenerate by default. Overriden by specific ore settings";
        public static final String ORE_CLUSTERS = "File path to .json file where the properties of one or more specific ore clusters are listed in a JSON array";


        public static final String REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS = "Comma separated list of integer days it takes for clusters to regenerate their ores." +
         " All clusters will regenerate on the same schedule. After sacrificing the specified item in the array below, the period length is reduced";
        public static final String REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS = "Comma separated list of items that will be used to reduce the number of days" +
         " between cluster regeneration. If the first value is NOT 'default', then clusters will not regenerate until the specified item has been sacrificed." +
          "In game, use the 'sacrificial altar' to sacrifice the specified item to trigger the next period length";
    }



}
