package com.holybuckets.orecluster;

import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.orecluster.command.CommandList;

import com.holybuckets.orecluster.core.OreClusterInterface;
import com.holybuckets.orecluster.core.OreClusterManager;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.LevelEvent;
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


    public static ModRealTimeConfig modRealTimeConfig = null;
    public static final Boolean DEBUG = true;

    /** Real Time Variables **/
    public static final Map<LevelAccessor, OreClusterManager> ORE_CLUSTER_MANAGER_BY_LEVEL = new HashMap<>();
    static {
        OreClusterInterface.initInstance( ORE_CLUSTER_MANAGER_BY_LEVEL );
    }

    public OreClustersAndRegenMain()
    {
        super();
        init();
        LoggerProject.logInit( "001000", this.getClass().getName() );
    }

    private void init() {
        EventRegistrar eventRegistrar = EventRegistrar.getInstance();
        eventRegistrar.registerOnLevelLoad( this::onLoadWorld );
        eventRegistrar.registerOnLevelUnload( this::onUnloadWorld );

        CommandList.register();
        ManagedOreClusterChunk.registerManagedChunkData();

    }

    public void onLoadWorld( LevelEvent.Load event )
    {
        LoggerProject.logDebug("001003", "**** WORLD LOAD EVENT ****");
        LevelAccessor level = event.getLevel();
        if( level.isClientSide() )
            return;

        if( modRealTimeConfig == null )
        {
            modRealTimeConfig = new ModRealTimeConfig( level );
        }

        if( !ORE_CLUSTER_MANAGER_BY_LEVEL.containsKey( level ) )
        {
            ORE_CLUSTER_MANAGER_BY_LEVEL.put( level, new OreClusterManager( level,  modRealTimeConfig ) );
        }

    }

    public void onUnloadWorld(LevelEvent.Unload event)
    {
        LoggerProject.logDebug("001004", "**** WORLD UNLOAD EVENT ****");

        for( OreClusterManager manager : ORE_CLUSTER_MANAGER_BY_LEVEL.values() ) {
            manager.shutdown();
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
