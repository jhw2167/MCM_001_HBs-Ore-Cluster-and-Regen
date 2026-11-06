package com.holybuckets.orecluster;

//MC Imports

//Forge Imports
import com.google.gson.JsonParser;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.orecluster.config.OreClusterConfig;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import com.holybuckets.orecluster.config.json.OreFeatureBiomeMatrixJsonConfig;
import com.holybuckets.orecluster.config.model.OreClusterJsonConfig;
import com.holybuckets.orecluster.config.model.OreFeatureBiomeMatrix;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

//Java Imports
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

//Project imports
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

import javax.annotation.Nullable;


/**
    * Class: RealTimeConfig
    * Description: The majority of fundamental mod config is in the config package.
    * This class will manifest that data behind simple methods and efficient data structures.

 */
public class ModRealTimeConfig
{
    public static final String CLASS_ID = "000";

    /**
     *  Base User configured data: defaultConfig and oreConfigs for particular ores
     */

    private static final Set<LevelAccessor> levelInit = new HashSet<>();
    private OreClusterConfigModel defaultConfig;
    private Map<OreClusterId, OreClusterConfigModel> oreConfigs;
    private List<OreClusterConfigModel> baseOreConfigs;
    private Map<String, OreClusterId> baseConfigIds;

    private Set<BlockState> validOreClusterBlocks;
    private OreFeatureBiomeMatrix oreFeatureBiomeMatrix = OreFeatureBiomeMatrix.EMPTY;

    /** We will batch checks for which chunks have clusters by the next CHUNK_NORMALIZATION_TOTAL chunks at a time
     thus the spawnrate is normalized to 256 chunks */
    public static final Integer CHUNK_NORMALIZATION_TOTAL = OreClusterConfigData.COreClusters.DEF_ORE_CLUSTER_SPAWNRATE_AREA;
    public static final Function<Integer, Double> CHUNK_DISTRIBUTION_STDV_FUNC = (mean ) -> {
        if( mean < 8 )
            return mean / 2.0;
         else
            return mean / (Math.log(mean) * 3);
        };

    /** As the player explores the world, we will batch new cluster spawns in
     * sizes of 1000. Each chunk will determine the clusters it owns extenting spirally from worldspawn.
     * This is not efficient, but ensures consistently between world seeds.
     *
     * Once a player loads a chunk more than 256 chunks from the worldspawn,
     * this method becomes inefficient and we will load chunks spirally considering this
     * new chunk as the center.
     */
    public static final Integer ORE_CLUSTER_DTRM_BATCH_SIZE_TOTAL = OreClusterConfigData.COreClusters.DEF_ORE_CLUSTER_SPAWNRATE_AREA; //chunks per batch
    public static final Integer ORE_CLUSTER_DTRM_RADIUS_STRATEGY_CHANGE = 128;  //square chunks


    //Using minecraft world seed as default
    public static Long CLUSTER_SEED = null;

    public ModRealTimeConfig(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(this::onBeforeServerStart, EventPriority.High);
        reg.registerOnLevelLoad(this::onLevelLoad, EventPriority.High);
        reg.registerOnServerStopped(this::onServerStopped, EventPriority.Low);

        this.oreConfigs = new HashMap<>();
        this.baseConfigIds = new HashMap<>();
        this.baseOreConfigs = new ArrayList<>();
        this.validOreClusterBlocks = new HashSet<>();

        LoggerProject.logInit("000000", this.getClass().getName());
    }


    //Constructor initializes the defaultConfigs and oreConfigs from forge properties
    public void onBeforeServerStart(ServerStartingEvent event)
    {
        this.oreConfigs = new HashMap<>();
        this.baseConfigIds = new HashMap<>();
        this.baseOreConfigs = new ArrayList<>();
        this.validOreClusterBlocks = new HashSet<>();

        OreClusterConfigData.COreClusters clusterConfig = OreClusterConfig.getActive().cOreClusters;
        this.defaultConfig = new OreClusterConfigModel(clusterConfig);
        defaultConfig.setConfigId();


        File configFile = new File(clusterConfig.oreClusterFileConfigPath);
        File defaultConfigFile = new File(OreClusterConfigData.COreClusters.DEF_ORE_CLUSTER_FILE_CONFIG_PATH);
        //config/HBOreClustersAndRegenConfigs.json
        //File configFile = new File("config/HBOreClustersAndRegenConfigs.json");
        //File defaultConfigFile = new File("config/HBOreClustersAndRegenConfigs.json");
        String jsonOreConfigData = HBUtil.FileIO.loadJsonConfigs( configFile, defaultConfigFile, OreClusterJsonConfig.DEFAULT_CONFIG );

        OreClusterJsonConfig jsonOreConfigs = new OreClusterJsonConfig(jsonOreConfigData);


        //Default configs will be used for all valid ore clusters unless overwritten
        for( Block validOreClusterBlock : defaultConfig.validOreClusterOreBlocks.stream().toList() )
        {
            defaultConfig.setOreClusterType(validOreClusterBlock.defaultBlockState());
            OreClusterConfigModel oreConfig = new OreClusterConfigModel( OreClusterConfigModel.serialize(defaultConfig) );
            baseOreConfigs.add( oreConfig );
        }
        defaultConfig.setOreClusterType( (BlockState) null);

        //Particular configs will overwrite the default data
        if(!jsonOreConfigs.getOreClusterConfigs().isEmpty()) baseOreConfigs.clear();
        for (OreClusterConfigModel oreConfig : jsonOreConfigs.getOreClusterConfigs()) {
            oreConfig.setConfigId();
            baseOreConfigs.add( oreConfig );
        }

        //Validate the defaultConfig minSpacingBetweenClusters
        validateClusterSpacingAndMinBlocks();


        if( defaultConfig.subSeed != null ) {
            CLUSTER_SEED = defaultConfig.subSeed;
        } else {
            CLUSTER_SEED = GeneralConfig.getInstance().getWorldSeed();
        }

        this.oreFeatureBiomeMatrix = loadOreFeatureBiomeMatrix(event.getServer().registryAccess(), clusterConfig);

        //serializer for consistency
        OreClusterJsonConfig newJsonOreConfigs = new OreClusterJsonConfig(baseOreConfigs);
        HBUtil.FileIO.serializeJsonConfigs( configFile, newJsonOreConfigs.serialize() );
    }


    /**
     * Hydrates configs with loaded biomes and levels
     * @param event
     */
    private void onLevelLoad(LevelLoadingEvent.Load event)
    {
        if(!(event.getLevel() instanceof ServerLevel)) return;
        if(this.levelInit.contains(event.getLevel())) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        List<OreClusterConfigModel> levelConfigs = baseOreConfigs.stream()
            .filter(model -> model.inLevel(level)).toList();

        //Register all clusters with all whitelisted biomes
        for(OreClusterConfigModel config : levelConfigs)
        {
            baseConfigIds.put(config.configId, OreClusterId.createBaseConfigId(config));
            oreConfigs.put(baseConfigIds.get(config.configId), config);

            List<OreClusterId> ids = OreClusterConfigModel.getIds(level, config);
            ids.forEach( id -> oreConfigs.put(id, config) );
            validOreClusterBlocks.add(config.oreClusterType);
        }

        this.levelInit.add(level);

    }


    //clear levelInit on serverStopped
    private void onServerStopped(ServerStoppedEvent event) {
        this.levelInit.clear();
        OreClusterId.shutdown();

        this.oreConfigs.clear();
        this.baseConfigIds.clear();
        this.baseOreConfigs.clear();
        this.validOreClusterBlocks.clear();
        this.oreFeatureBiomeMatrix = OreFeatureBiomeMatrix.EMPTY;
    }

        /*
         *  Getters
         */

        public List<OreClusterConfigModel> getBaseOreConfigs() {
            return baseOreConfigs;
        }

        public Map<OreClusterId, OreClusterConfigModel> getOreConfigs() {
            return new HashMap<>(oreConfigs);
        }

        public @Nullable OreClusterConfigModel getOreConfigByOre(ResourceLocation ore) {
            OreClusterId id = OreClusterId.getId(
                null, null, ore
            );
            return oreConfigs.get(id);
        }

        public List<OreClusterConfigModel> getAllOreConfigByOre(Block b) {
            Set<OreClusterConfigModel> configs = new HashSet<>();
            //BlockState b = HBUtil.BlockUtil.blockNameToBlock(ore.toString()).defaultBlockState();
            for( OreClusterConfigModel config : baseOreConfigs ) {
                if( config.oreClusterType != null && config.oreClusterType.equals(b.defaultBlockState()) ) {
                    configs.add(config);
                }
            }
            return configs.stream().toList();
        }

        public OreClusterConfigModel getOreConfigModel(OreClusterId id) {
            return oreConfigs.get(id);
        }

        public OreClusterId getOreConfigId(Level l, Biome b, Block bl) {
            return OreClusterId.getId(l, b, bl);
        }

        /**
         * @param id - int id for read/write
         * @return
         */
        public OreClusterId getOreConfigId(int id) {
            if( oreConfigs == null ) return null;
            for (OreClusterId oreId : oreConfigs.keySet()) {
                if( oreId.getId() == id ) return oreId;
            }
            return null;
        }

        public OreClusterConfigModel getBaseOreConfig(String configId) {
            for( OreClusterConfigModel config : baseOreConfigs ) {
                if( config.configId.equals(configId) ) return config;
            }
            return null;
        }

        public OreClusterId getBaseOreConfigId(String configId) {
            return baseConfigIds.get(configId);
        }

        public Set<OreClusterId> getAllOreConfigIdsByOre(Block b) {
            return oreConfigs.keySet().stream().filter( id -> id.filter(b) ).collect(Collectors.toSet());
        }

        public Set<OreClusterId> getAllOreConfigIdsByBiome(Level l, Biome b) {
            return oreConfigs.keySet().stream().filter( id -> id.filter(l,b) ).collect(Collectors.toSet());
        }

        public OreClusterConfigModel getDefaultConfigModel() {
            return defaultConfig;
        }

        public OreFeatureBiomeMatrix getOreFeatureBiomeMatrix() {
            return oreFeatureBiomeMatrix;
        }

        /**
         *  Setters
         */

        public void setDefaultConfig(OreClusterConfigModel defaultConfig) {
            this.defaultConfig = defaultConfig;
        }


    /**
     * Helper methods
     */

    private OreFeatureBiomeMatrix loadOreFeatureBiomeMatrix(RegistryAccess registries, OreClusterConfigData.COreClusters clusterConfig)
    {
        File configFile = new File(clusterConfig.oreFeatureBiomeMatrixConfig);
        File defaultConfigFile = new File(OreFeatureBiomeMatrixJsonConfig.DEF_CONFIG_FILE_PATH);
        String json = HBUtil.FileIO.loadJsonConfigs(configFile, defaultConfigFile, OreFeatureBiomeMatrixJsonConfig.DEFAULT_CONFIG);

        List<OreFeatureBiomeMatrixJsonConfig> entries;
        try {
            entries = OreFeatureBiomeMatrixJsonConfig.parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            String msg = String.format(
                "Failed to parse ore feature biome matrix JSON: %s. Error:\n %s.\n\nDefault configs will be applied",
                configFile.getAbsolutePath(), e.getMessage());
            LoggerProject.logError("000002", msg);
            entries = OreFeatureBiomeMatrixJsonConfig.buildDefaultConfig();
        }

        OreFeatureBiomeMatrix matrix = resolveOreFeatureBiomeMatrix(entries, registries);
        LoggerProject.logInfo("000001", "Loaded ore feature frequencies for " + matrix.size() + " biome(s)");
        return matrix;
    }

    private static OreFeatureBiomeMatrix resolveOreFeatureBiomeMatrix(List<OreFeatureBiomeMatrixJsonConfig> configs, RegistryAccess registries)
    {
        Registry<Biome> biomeRegistry = registries.registryOrThrow(Registries.BIOME);
        Map<Biome, Map<Block, Float>> resolved = new HashMap<>();
        int skipped = 0;

        for (OreFeatureBiomeMatrixJsonConfig config : configs) {
            ResourceLocation biomeId = ResourceLocation.tryParse(config.getBiomeId());
            Biome biome = biomeId == null ? null : biomeRegistry.get(biomeId);
            if (biome == null) {
                LoggerProject.logWarning("000003", "Unknown biome '" + config.getBiomeId() + "' in ore feature biome matrix; skipping");
                skipped++;
                continue;
            }

            Map<Block, Float> ores = new HashMap<>();
            for (Map.Entry<String, Float> entry : config.getFrequencies().entrySet()) {
                ResourceLocation oreId = ResourceLocation.tryParse(entry.getKey());
                Block ore = oreId == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(oreId);
                if (ore == Blocks.AIR || entry.getValue() < 0f) {
                    LoggerProject.logWarning("000004", "Invalid ore entry '" + entry.getKey() + "': " + entry.getValue()
                        + " under biome '" + config.getBiomeId() + "'; skipping");
                    skipped++;
                    continue;
                }
                ores.put(ore, entry.getValue());
            }
            resolved.put(biome, ores);
        }

        LoggerProject.logInfo("000005", "Resolved ore feature biome matrix, skipped " + skipped + " invalid entrie(s)");
        return new OreFeatureBiomeMatrix(resolved);
    }

     private void validateClusterSpacingAndMinBlocks()
     {
         //Mathematically validate the defaultConfig minSpacingBetweenClusters
         // is acceptable considering the provided spawnrate of each specific ore cluster
         int totalSpawnRatePerAreaSquared = 0; //256 chunks squared -> 16x16
         for( OreClusterConfigModel oreConfig : this.baseOreConfigs) {
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

    //* Static Utility

    public OreClusterConfigModel getOreConfig(Level l, Biome b, Block bl) {
        OreClusterId id = OreClusterId.getId(l, b, bl);
        if( id == null ) return null;
        return  oreConfigs.get( id );
    }

    public OreClusterConfigModel getOreConfig(OreClusterId id) {
        if( id == null ) return null;
        return oreConfigs.get( id );
    }

    public OreClusterConfigModel getOreConfig(int id) {
        OreClusterId oreId = getOreConfigId( id );
        if( oreId == null ) return null;
        return oreConfigs.get( oreId );
    }

    /**
     *
     * @param sectionY - different scale per each dimension to account for negative values, may be negative
     * @param config
     * @return true if no config is provided or if the pos is in config range, false otherwise
     */
    public boolean testValidYSpawn(OreClusterConfigModel config, int sectionY) {
        if(config == null) return true;
        BlockPos pos = new BlockPos(0,(sectionY*16) + 8,0);
        return  testValidYSpawn( config , pos);
    }

    /**
     * @param sectionY - different scale per each dimension to account for negative values, may be negative
     * @param id - id of ore config
     * @return false if id is null or not found
     */
    public boolean testValidYSpawn(OreClusterId id, int sectionY) {
        if(id == null || !oreConfigs.containsKey(id)) return false;
        return testValidYSpawn( oreConfigs.get(id) , sectionY);
    }

    /**
     *
     * @param pos
     * @param config
     * @return true if no config is provided or if the pos is in config range, false otherwise
     */
    public static boolean testValidYSpawn(OreClusterConfigModel config, BlockPos pos) {
        if( config == null ) return true;
        if( config.oreClusterMaxYLevelSpawn == null ) return true;
        if( pos.getY() > config.oreClusterMaxYLevelSpawn ) return false;
        if( pos.getY() < config.oreClusterMinYLevelSpawn ) return false;

        return true;
    }

    /**
     * If the config is null, then the cluster will not spawn
     * @param state
     * @return true if the oreClusterSpawnRate is greater than 0, false otherwise
     */
    public boolean clustersDoSpawn(Level l, Biome b, BlockState state) {
        return clustersDoSpawn( getOreConfig(l, b, state.getBlock()) );
    }

    /**
     * If the config is null, then the cluster will not spawn
     * @param config
     * @return true if the oreClusterSpawnRate is greater than 0, false otherwise
     */
    public static boolean clustersDoSpawn(OreClusterConfigModel config) {
        if( config == null ) return false;
        return config.oreClusterSpawnRate > 0;
    }


    public static boolean doesLevelMatch(OreClusterConfigModel config, LevelAccessor level) {
        if( config == null ) return false;
        if( config.oreClusterDimensionId == null ) return false;
        if( level == null ) return false;
        LevelAccessor oreLevel = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, config.oreClusterDimensionId);
        return level.equals(oreLevel);
    }

    /**
     * Checks if clusters spawn for a given OreClusterId
     * @param id The OreClusterId to check
     * @return false if id is null or not found in configs, otherwise checks if clusters spawn for that config
     */
    public boolean clustersDoSpawn(OreClusterId id) {
        if (id == null) return false;
        OreClusterConfigModel config = oreConfigs.get(id);
        return clustersDoSpawn(config);
    }

    /**
     * Checks if a level matches for a given OreClusterId
     * @param id The OreClusterId to check
     * @param level The level to check against
     * @return false if id is null or not found in configs, otherwise checks if level matches that config
     */
    public boolean doesLevelMatch(OreClusterId id, LevelAccessor level) {
        if (id == null) return false;
        OreClusterConfigModel config = oreConfigs.get(id);
        return doesLevelMatch(config, level);
    }

    public boolean maybeHasBlock(BlockState defaultState) {
        return validOreClusterBlocks.contains(defaultState);
    }


}
//END CLASS
