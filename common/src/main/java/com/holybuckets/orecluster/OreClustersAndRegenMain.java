package com.holybuckets.orecluster;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.foundation.event.EventRegistrar;

import com.holybuckets.orecluster.command.CommandList;
import com.holybuckets.orecluster.config.OreClusterConfig;
import com.holybuckets.orecluster.core.OreClusterInterface;
import com.holybuckets.orecluster.core.OreClusterManager;
import com.holybuckets.orecluster.core.OreClusterRegenManager;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.minecraft.world.level.LevelAccessor;

import java.util.HashMap;
import java.util.Map;

// The value here should match an entry in the META-INF/mods.toml file
public class OreClustersAndRegenMain
{
    public static final String CLASS_ID = "001";    //unused variable, value will be used for logging messages

    // Define mod id in a common place for everything to reference
    public static final String MODID = "hbs_ore_clusters_and_regen";
    public static final String NAME = "HBs Ore Clusters and Regen";
    public static final String VERSION = "1.0.0f";
    public static final Boolean DEBUG = true;

    public static OreClustersAndRegenMain INSTANCE = null;
    public ModRealTimeConfig modRealTimeConfig = null;

    /** Real Time Variables **/
    public Map<LevelAccessor, OreClusterManager> ORE_CLUSTER_MANAGER_BY_LEVEL = new HashMap<>();
    public OreClusterRegenManager ORE_CLUSTER_REGEN_MANAGER = null;

    public OreClustersAndRegenMain()
    {
        super();
        init();
        INSTANCE = this;
        LoggerProject.logInit( "001000", this.getClass().getName() );
    }

    private void init()
    {
        OreClusterConfig.initialize();
        ManagedOreClusterChunk.registerManagedChunkData();

        CommandList.register();
        EventRegistrar eventRegistrar = EventRegistrar.getInstance();

        this.ORE_CLUSTER_MANAGER_BY_LEVEL = new HashMap<>();
        this.modRealTimeConfig = new ModRealTimeConfig();
        this.ORE_CLUSTER_REGEN_MANAGER = new OreClusterRegenManager( eventRegistrar, modRealTimeConfig, ORE_CLUSTER_MANAGER_BY_LEVEL );
        OreClusterInterface.initInstance( ORE_CLUSTER_MANAGER_BY_LEVEL );

        eventRegistrar.registerOnLevelLoad( this::onLoadWorld, EventPriority.High );
        eventRegistrar.registerOnLevelUnload( this::onUnloadWorld, EventPriority.Low );


        /*
        WaystonesConfig.initialize();
        ModStats.initialize();
        ModEventHandlers.initialize();
        ModBlocks.initialize(Balm.getBlocks());
        ModBlockEntities.initialize(Balm.getBlockEntities()); */
        //ModNetworking.initialize(Balm.getNetworking());
        /* ModItems.initialize(Balm.getItems());
        ModMenus.initialize(Balm.getMenus());
        ModWorldGen.initialize(Balm.getWorldGen());
        ModRecipes.initialize(Balm.getRecipes());
        */

    }

    public static Map<LevelAccessor, OreClusterManager> getManagers() {
        return INSTANCE.ORE_CLUSTER_MANAGER_BY_LEVEL;
    }

    public void onLoadWorld( LevelLoadingEvent.Load event )
    {
        LoggerProject.logDebug("001003", "**** WORLD LOAD EVENT ****");
        LevelAccessor level = event.getLevel();
        if( level.isClientSide() ) return;

        if( !ORE_CLUSTER_MANAGER_BY_LEVEL.containsKey( level ) )
        {
            if( DEBUG && ( !HBUtil.LevelUtil.toLevelId( level ).contains("overworld") )) return;
            ORE_CLUSTER_MANAGER_BY_LEVEL.put( level, new OreClusterManager( level,  modRealTimeConfig ) );
        }

    }

    public void onUnloadWorld(LevelLoadingEvent.Unload event)
    {
        LoggerProject.logDebug("001004", "**** WORLD UNLOAD EVENT ****");
        LevelAccessor level = event.getLevel();
        if( level.isClientSide() ) return;

        OreClusterManager m = ORE_CLUSTER_MANAGER_BY_LEVEL.remove( level );
        if( m != null ) m.shutdown();
        if( ORE_CLUSTER_MANAGER_BY_LEVEL.isEmpty() ) {
            ORE_CLUSTER_REGEN_MANAGER.shutdown();
        }
    }



    /*
    public static void init(final FMLCommonSetupEvent event) {
        AllFluids.registerFluidInteractions();

        event.enqueueWork(() -> {
            // TODO: custom registration should all happen in one place
            // Most registration happens in the constructor.
            // These registrations use Create's registered objects directly so they must run after registration has finished.
            BuiltinPotatoProjectileTypes.register();
            BoilerHeaters.registerDefaults();
            // --

            AttachedRegistry.unwrapAll();
            AllAdvancements.register();
            AllTriggers.register();
        });
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(ID, path);
    }
    */
}
