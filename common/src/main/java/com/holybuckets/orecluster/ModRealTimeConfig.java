package com.holybuckets.orecluster;

//MC Imports

//Forge Imports
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.config.COreClusters;
import com.holybuckets.orecluster.config.model.OreClusterJsonConfig;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

//Java Imports
import java.io.File;
import java.util.*;
import java.util.function.Function;

//Project imports
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.config.AllConfigs;


/**
    * Class: RealTimeConfig
    *
    * Description: The majority of fundamental mod config is in the config package.
    * This class will manifest that data behind simple methods and efficient data structures.

 */
public class ModRealTimeConfig
{
    public static final String CLASS_ID = "000";

    /**
     *  Base User configured data: defaultConfig and oreConfigs for particular ores
     */

    private OreClusterConfigModel defaultConfig;
    private Map<Block, OreClusterConfigModel> oreConfigs;

    /** We will batch checks for which chunks have clusters by the next CHUNK_NORMALIZATION_TOTAL chunks at a time
     thus the spawnrate is normalized to 256 chunks */
    public static final Integer CHUNK_NORMALIZATION_TOTAL = COreClusters.DEF_ORE_CLUSTER_SPAWNRATE_AREA;
    public static final Function<Integer, Double> CHUNK_DISTRIBUTION_STDV_FUNC = (mean ) -> {
        if( mean < 8 )
            return mean / 2.0;
         else
            return mean / (Math.log(mean) * 3);
        };

    /** As the player explores the world, we will batch new cluster spawns in
     * sizes of 1024. Each chunk will determine the clusters it owns extenting spirally from worldspawn.
     * This is not efficient, but ensures consistently between world seeds.
     *
     * Once a player loads a chunk more than 256 chunks from the worldspawn,
     * this method becomes inefficient and we will load chunks spirally considering this
     * new chunk as the center.
     */
    public static final Integer ORE_CLUSTER_DTRM_BATCH_SIZE_TOTAL = 256; //chunks per batch
    public static final Integer ORE_CLUSTER_DTRM_RADIUS_STRATEGY_CHANGE = 256;  //square chunks


    //Using minecraft world seed as default
    public static Long CLUSTER_SEED = null;

        //Constructor initializes the defaultConfigs and oreConfigs from forge properties
        public ModRealTimeConfig(LevelAccessor level)
        {

            COreClusters clusterConfig = AllConfigs.server().cOreClusters;
            defaultConfig = new OreClusterConfigModel(clusterConfig);

            //Create new oreConfig for each element in cOreClusters list
            oreConfigs = new HashMap<Block, OreClusterConfigModel>();

            //File configFile = new File(clusterConfig.oreClusters.get());
            //File defaultConfigFile = new File(clusterConfig.oreClusters.getDefault());
            //config/HBOreClustersAndRegenConfigs.json
            File configFile = new File("config/HBOreClustersAndRegenConfigs.json");
            File defaultConfigFile = new File("config/HBOreClustersAndRegenConfigs.json");
            String jsonOreConfigData = HBUtil.FileIO.loadJsonConfigs( configFile, defaultConfigFile, OreClusterJsonConfig.DEFAULT_CONFIG );

            OreClusterJsonConfig jsonOreConfigs = new OreClusterJsonConfig(jsonOreConfigData);


            //Default configs will be used for all valid ore clusters unless overwritten
            for( Block validOreClusterBlock : defaultConfig.validOreClusterOreBlocks.stream().toList() )
            {
                defaultConfig.setOreClusterType(validOreClusterBlock);
                OreClusterConfigModel oreConfig = new OreClusterConfigModel( OreClusterConfigModel.serialize(defaultConfig) );
                oreConfigs.put(validOreClusterBlock, oreConfig );
            }
            defaultConfig.setOreClusterType((Block) null);

            //Particular configs will overwrite the default data
            for (OreClusterConfigModel oreConfig : jsonOreConfigs.getOreClusterConfigs())
            {
                oreConfigs.put(oreConfig.oreClusterType, oreConfig);
            }

            //Validate the defaultConfig minSpacingBetweenClusters
            validateClusterSpacingAndMinBlocks();


            if( !defaultConfig.subSeed.equals(0L) ) {
                CLUSTER_SEED = defaultConfig.subSeed;
            } else {
                CLUSTER_SEED = GeneralConfig.getInstance().getWorldSeed();
            }

            LoggerProject.logInit("000000", this.getClass().getName());
        }

        /**
         *  Getters
         */

        public Map<Block, OreClusterConfigModel> getOreConfigs() {
            return oreConfigs;
        }

        public OreClusterConfigModel getDefaultConfig() {
            return defaultConfig;
        }

        /**
         *  Setters
         */

        public void setDefaultConfig(OreClusterConfigModel defaultConfig) {
            this.defaultConfig = defaultConfig;
        }

        public void setOreConfigs(Map<Block, OreClusterConfigModel> oreConfigs) {
            this.oreConfigs = oreConfigs;
        }

    /**
     * Helper methods
     */

     private void validateClusterSpacingAndMinBlocks()
     {
         //Mathematically validate the defaultConfig minSpacingBetweenClusters
         // is acceptable considering the provided spawnrate of each specific ore cluster
         int totalSpawnRatePerAreaSquared = 0; //256 chunks squared -> 16x16
         for( OreClusterConfigModel oreConfig : this.oreConfigs.values() ) {
             totalSpawnRatePerAreaSquared += oreConfig.oreClusterSpawnRate;
         }
         int reservedBlocksSquaredPerCluster = (int) Math.pow(defaultConfig.minChunksBetweenOreClusters, 2);

         //Avoid divide by zero adjustment
         if( reservedBlocksSquaredPerCluster == 0 || totalSpawnRatePerAreaSquared == 0 )
             return;


         int maxClustersPerAreaSquared = CHUNK_NORMALIZATION_TOTAL / reservedBlocksSquaredPerCluster;
         if( totalSpawnRatePerAreaSquared > maxClustersPerAreaSquared )
         {
             int newMinChunks = (int) Math.sqrt( CHUNK_NORMALIZATION_TOTAL / totalSpawnRatePerAreaSquared );
             StringBuilder warn = new StringBuilder();
             warn.append("The net ore cluster spawn rate exceeds the expected maximum number of clusters in a ");
             warn.append(CHUNK_NORMALIZATION_TOTAL);
             warn.append(" square chunk area: ");
             warn.append(maxClustersPerAreaSquared);
             warn.append(" square chunks alloted by ");
             warn.append(defaultConfig.minChunksBetweenOreClusters);
             warn.append(" chunks between clusters. While ");
             warn.append(totalSpawnRatePerAreaSquared);
             warn.append(" clusters are would be observed on average. minClustersBetweenChunks reduced to ");
             defaultConfig.minChunksBetweenOreClusters = (int) Math.sqrt( newMinChunks ) - 1;
             warn.append(defaultConfig.minChunksBetweenOreClusters);

         }
     }


}
//END CLASS