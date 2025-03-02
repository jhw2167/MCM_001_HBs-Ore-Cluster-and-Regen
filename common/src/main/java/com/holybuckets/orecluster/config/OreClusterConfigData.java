package com.holybuckets.orecluster.config;

import com.holybuckets.foundation.HBUtil.Validator.ConfigNumber;
import com.holybuckets.orecluster.Constants;
import net.blay09.mods.balm.api.config.BalmConfigData;
import net.blay09.mods.balm.api.config.Comment;
import net.blay09.mods.balm.api.config.Config;
import net.blay09.mods.balm.api.config.IgnoreConfig;

import java.util.Arrays;
import java.util.HashSet;

@Config(Constants.MOD_ID)
public class OreClusterConfigData implements BalmConfigData {
    public COreClusters cOreClusters = new COreClusters();

    public static class COreClusters
    {

        //Units of area that denominates ORE_CLUSTER_SPAWN_RATE
        @IgnoreConfig("Constant")
        public static final int DEF_ORE_CLUSTER_SPAWNRATE_AREA = 1000;

        //Defaults
        public static final String DEF_SUB_SEED = "";

        public static final String DEF_VALID_ORE_CLUSTER_ORE_BLOCKS = "minecraft:iron_ore";

        public static final ConfigNumber<Integer> DEF_ORE_CLUSTER_SPAWN_RATE = new ConfigNumber<>("oreClusterSpawnrate", 16, 1, DEF_ORE_CLUSTER_SPAWNRATE_AREA);
        public static final String DEF_ORE_CLUSTER_VOLUME = "8x8x4";

        public static final ConfigNumber<Float> DEF_ORE_CLUSTER_DENSITY = new ConfigNumber<>("oreClusterDensity", 1f, 0f, 1f);
        public static final String DEF_ORE_CLUSTER_SHAPE = "any";
        public static final ConfigNumber<Integer> DEF_ORE_CLUSTER_MAX_Y_LEVEL_SPAWN = new ConfigNumber<>("oreClusterMaxYLevelSpawn", 256, 0, 512);
        public static final ConfigNumber<Integer> DEF_MIN_CHUNKS_BETWEEN_ORE_CLUSTERS = new ConfigNumber<>("minChunksBetweenOreClusters", 0, 0, 32);
        public static final ConfigNumber<Integer> DEF_MAX_CHUNKS_BETWEEN_ORE_CLUSTERS = new ConfigNumber<>("maxChunksBetweenOreClusters", 2, 2, 32);
        public static final ConfigNumber<Float> DEF_ORE_VEIN_MODIFIER = new ConfigNumber<>("oreVeinModifier", 1f, 0f, 1f);
        public static final String DEF_ORE_CLUSTER_NONREPLACEABLE_BLOCKS = "minecraft:end_portal_frame,minecraft:bedrock";
        public static final String DEF_ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS = "minecraft:stone,minecraft:air";

        public static final boolean DEF_REGENERATE_ORE_CLUSTERS = true;
        public static final String DEF_REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS = "28,24,16,8";
        public static final String DEF_REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS = "default,minecraft:blaze_powder," +
            "minecraft:dragon_egg,minecraft:nether_star";
        public static final String DEF_ORE_CLUSTER_FILE_CONFIG_PATH = "config/HBOreClustersAndRegenConfigs.json";

        //Ranges
        public static final String DEF_MIN_ORE_CLUSTER_VOLUME = "0x0x0";
        public static final String DEF_MAX_ORE_CLUSTER_VOLUME = "64x64x64";
        public static final HashSet<String> DEF_ORE_CLUSTER_VALID_SHAPES = new HashSet<>(Arrays.asList("bowl", "anvil", "shale", "any"));



        @Comment("Sub-seed used to generate random numbers for ore cluster generation - by default, Ore cluster generation uses the world seed to determine which chunks have ore clusters and their shape and size. By assigning a sub-seed, you can adjust this randomness for the same world")
        public String subSeed = DEF_SUB_SEED;

        @Comment("List of blocks that the mod will attempt to create clusters for upon chunk load. Clusters created from these blocks will take all default ore cluster parameters unless overridden. If you are going to override the default parameters for an ore anyways, you don't need to include it in this list")
        public String validOreClusterOreBlocks = DEF_VALID_ORE_CLUSTER_ORE_BLOCKS;

        @Comment("Defines the default frequency of ore clusters. Takes an integer as the number of expected ore clusters per 1000 chunks")
        public int oreClusterSpawnRate = DEF_ORE_CLUSTER_SPAWN_RATE.get();

        @Comment("Specifies the default dimensions of a cluster. <X>x<Y>x<Z>. The true cluster will always be smaller than this box because it will choose a shape that roughly fits inside it, max 64x64x64 else it will revert to the default 16x16x16")
        public String oreClusterVolume = DEF_ORE_CLUSTER_VOLUME;

        @Comment("Determines the density of ore within a cluster. To reduce density ore blocks within the cluster will be replaced with blocks from 'defaultOreClusterReplaceableEmptyBlock' below")
        public float oreClusterDensity = DEF_ORE_CLUSTER_DENSITY.get();

        @Comment("Defines the shape of the ore cluster. Options are 'bowl', 'anvil', 'shale', 'flat' or 'any'. Defaults to any, which takes a random shape")
        public String oreClusterShape = DEF_ORE_CLUSTER_SHAPE;

        @Comment("Maximum Y-level at which clusters can spawn")
        public int oreClusterMaxYLevelSpawn = DEF_ORE_CLUSTER_MAX_Y_LEVEL_SPAWN.get();

        @Comment("Minimum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS - this is a rough guideline, the random generation is not perfect")
        public int minChunksBetweenOreClusters = DEF_MIN_CHUNKS_BETWEEN_ORE_CLUSTERS.get();

        @Comment("Maximum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS - this is a rough guideline, the random generation is not perfect")
        public int maxChunksBetweenOreClusters = DEF_MAX_CHUNKS_BETWEEN_ORE_CLUSTERS.get();

        @Comment("Scales the presence of normal (small) ore veins between 0 and 1. This mod replaces existing ore veins in real time with the specified first block in 'defaultOreClusterReplaceableEmptyBlock' block so can only reduce the frequency of ore veins, not increase it")
        public float oreVeinModifier = DEF_ORE_VEIN_MODIFIER.get();

        @Comment("List of blocks that should not be replaced by the specified ore during cluster generation. For example, if you don't want ore clusters to replace bedrock - which is very reasonable - you would add 'minecraft:bedrock' to this list")
        public String oreClusterNonreplaceableBlocks = DEF_ORE_CLUSTER_NONREPLACEABLE_BLOCKS;

        @Comment("Block used to fill in the ore cluster shape when we want the cluster to be more sparse this field can take multiple comma seperated blocks; but only the first block will be used to replace ore veins if ORE_VEIN_MODIFIER is below 1")
        public String oreClusterReplaceableEmptyBlocks = DEF_ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS;

        @Comment("Flag indicating if ore clusters should regenerate by default. Overriden by specific ore settings")
        public boolean regenerateOreClusters = DEF_REGENERATE_ORE_CLUSTERS;

        @Comment("File path to .json file where the properties of one or more specific ore clusters are listed in a JSON array")
        public String oreClusterFileConfigPath = DEF_ORE_CLUSTER_FILE_CONFIG_PATH;

        @Comment("Comma separated list of integer days it takes for clusters to regenerate their ores. All clusters will regenerate on the same schedule. After sacrificing the specified item in the array below, the period length is reduced")
        public String regenerateOreClusterPeriodLengths = DEF_REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS;

        @Comment("Comma separated list of items that will be used to reduce the number of days between cluster regeneration. If the first value is NOT 'default', then clusters will not regenerate until the specified item has been sacrificed. In game, use the 'sacrificial altar' to sacrifice the specified item to trigger the next period length")
        public String regenerateOreClusterUpgradeItems = DEF_REGENERATE_ORE_CLUSTER_UPGRADE_ITEMS;



    }

}
