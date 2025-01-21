package com.holybuckets.orecluster.config;

import com.holybuckets.orecluster.Constants;
import net.blay09.mods.balm.api.config.BalmConfigData;
import net.blay09.mods.balm.api.config.Comment;
import net.blay09.mods.balm.api.config.Config;

@Config(Constants.MOD_ID)
public class OreClusterConfigData implements BalmConfigData {
    public COreClusters cOreClusters = new COreClusters();

    public static class COreClusters {

        //Defaults

        //Units of area that denominates ORE_CLUSTER_SPAWN_RATE
        public int ORE_CLUSTER_SPAWNRATE_AREA = 256;


        @Comment("Sub-seed used to generate random numbers for ore cluster generation - by default, Ore cluster generation uses the world seed to determine which chunks have ore clusters and their shape and size. By assigning a sub-seed, you can adjust this randomness for the same world")
        public String SUB_SEED = "";

        @Comment("List of blocks that the mod will attempt to create clusters for upon chunk load. Clusters created from these blocks will take all default ore cluster parameters unless overridden. If you are going to override the default parameters for an ore anyways, you don't need to include it in this list")
        public String VALID_ORE_CLUSTER_ORE_BLOCKS = "minecraft:iron_ore,minecraft:diamond_ore,minecraft:gold_ore,minecraft:coal_ore";

        @Comment("Defines the default frequency of ore clusters. Takes an integer as the number of expected ore clusters per 256 chunks")
        public int ORE_CLUSTER_SPAWN_RATE = 16;

        @Comment("Specifies the default dimensions of a cluster. <X>x<Y>x<Z>. The true cluster will always be smaller than this box because it will choose a shape that roughly fits inside it, max 64x64x64 else it will revert to the default 16x16x16")
        public String ORE_CLUSTER_VOLUME = "16x16x16";

        @Comment("Determines the density of ore within a cluster. To reduce density ore blocks within the cluster will be replaced with blocks from 'defaultOreClusterReplaceableEmptyBlock' below")
        public float ORE_CLUSTER_DENSITY = 0.80f;

        @Comment("Defines the shape of the ore cluster. Options are 'bowl', 'anvil', 'shale', 'flat' or 'any'. Defaults to any, which takes a random shape")
        public String ORE_CLUSTER_SHAPE = "any";

        @Comment("Maximum Y-level at which clusters can spawn")
        public int ORE_CLUSTER_MAX_Y_LEVEL_SPAWN = 256;

        @Comment("Minimum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS - this is a rough guideline, the random generation is not perfect")
        public int MIN_CHUNKS_BETWEEN_ORE_CLUSTERS = 0;

        @Comment("Maximum number of chunks between ore any clusters - AFFECTS ALL ORE CLUSTERS - this is a rough guideline, the random generation is not perfect")
        public int MAX_CHUNKS_BETWEEN_ORE_CLUSTERS = 9;

        @Comment("Scales the presence of normal (small) ore veins between 0 and 1. This mod replaces existing ore veins in real time with the specified first block in 'defaultOreClusterReplaceableEmptyBlock' block so can only reduce the frequency of ore veins, not increase it")
        public float ORE_VEIN_MODIFIER = 1f;

        @Comment("List of blocks that should not be replaced by the specified ore during cluster generation. For example, if you don't want ore clusters to replace bedrock - which is very reasonable - you would add 'minecraft:bedrock' to this list")
        public String ORE_CLUSTER_NONREPLACEABLE_BLOCKS = "minecraft:end_portal_frame,minecraft:bedrock";

        @Comment("Block used to fill in the ore cluster shape when we want the cluster to be more sparse this field can take multiple comma seperated blocks; but only the first block will be used to replace ore veins if ORE_VEIN_MODIFIER is below 1")
        public String ORE_CLUSTER_REPLACEABLE_EMPTY_BLOCKS = "minecraft:stone,minecraft:air";

        @Comment("Flag indicating if ore clusters should regenerate by default. Overriden by specific ore settings")
        public boolean REGENERATE_ORE_CLUSTERS = true;

        @Comment("Comma separated list of integer days it takes for clusters to regenerate their ores. All clusters will regenerate on the same schedule. After sacrificing the specified item in the array below, the period length is reduced")
        public String REGENERATE_ORE_CLUSTER_PERIOD_LENGTHS = "28,24,16,8";

        @Comment("Comma separated list of items that will be used to reduce the number of days between cluster regeneration. If the first value is NOT 'default', then clusters will not regenerate until the specified item has been sacrificed. In game, use the 'sacrificial altar' to sacrifice the specified item to trigger the next period length")
        public String REGENERATE_ORE_CLUSTER_PERIOD_UPGRADE_ITEMS = "default,minecraft:blaze_powder,minecraft:dragon_egg,minecraft:nether_star";

        @Comment("File path to .json file where the properties of one or more specific ore clusters are listed in a JSON array")
        public String ORE_CLUSTER_FILE_CONFIG_PATH = "config/HBOreClustersAndRegenConfigs.json";

    }

}
