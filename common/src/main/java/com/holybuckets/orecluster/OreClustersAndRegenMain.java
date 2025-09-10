package com.holybuckets.orecluster;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;

import com.holybuckets.orecluster.command.CommandList;
import com.holybuckets.orecluster.config.OreClusterConfig;
import com.holybuckets.orecluster.core.*;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.HashMap;
import java.util.Map;

// The value here should match an entry in the META-INF/mods.toml file
public class OreClustersAndRegenMain
{
    public static final String CLASS_ID = "001";    //unused variable, value will be used for logging messages

    // Define mod id in a common place for everything to reference
    public static final String MODID = Constants.MOD_ID;
    public static final String NAME = "HBs Ore Clusters and Regen";
    public static final String VERSION = "1.0.0f";
    public static final Boolean DEBUG = false;

    public static OreClustersAndRegenMain INSTANCE;
    public ModRealTimeConfig modRealTimeConfig;

    /** Real Time Variables **/
    public Map<LevelAccessor, OreClusterManager> oreClusterManagers;
    public OreClusterRegenManager regenManager;
    public OreClusterApi oreClusterApi;
    public OreClusterHealthCheck oreClusterHealthCheck;

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

        OreClusterManager.init( eventRegistrar );
        this.oreClusterManagers = OreClusterManager.MANAGERS;
        this.modRealTimeConfig = new ModRealTimeConfig(eventRegistrar);
        this.regenManager = new OreClusterRegenManager( eventRegistrar, modRealTimeConfig, oreClusterManagers);
        this.oreClusterApi = new OreClusterApi(oreClusterManagers, modRealTimeConfig, regenManager);
        this.oreClusterHealthCheck = new OreClusterHealthCheck( eventRegistrar, oreClusterApi, oreClusterManagers);


        eventRegistrar.registerOnLevelLoad( this::onLoadWorld, EventPriority.Normal );
        //eventRegistrar.registerOnLevelUnload( this::onUnloadWorld, EventPriority.Low );
        eventRegistrar.registerOnServerStopped(this::onServerStopped, EventPriority.Low);

        OreClusterBlockStateTracker.init(this.modRealTimeConfig);


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
        return INSTANCE.oreClusterManagers;
    }

    public void onLoadWorld( LevelLoadingEvent.Load event )
    {
        //if(true) return;
        LoggerProject.logDebug("001003", "**** WORLD LOAD EVENT ****");
        Level level = (Level) event.getLevel();
        if( level.isClientSide() ) return;

        if( !oreClusterManagers.containsKey( level ) )
        {
            //if( DEBUG && ( !HBUtil.LevelUtil.toLevelId( level ).contains("overworld") )) return;
            oreClusterManagers.put( level, new OreClusterManager( level,  modRealTimeConfig ) );
        }

    }

    //Not stable, may happen at runtime
    public void onUnloadWorld(LevelLoadingEvent.Unload event) {
        LoggerProject.logDebug("001004", "**** WORLD UNLOAD EVENT ****");
    }

    public void onServerStopped(ServerStoppedEvent event) {
        LoggerProject.logDebug("001005", "**** SERVER STOPPED EVENT ****");
        
        // Shutdown all managers
        for (OreClusterManager manager : oreClusterManagers.values()) {
            manager.shutdown();
        }
        oreClusterManagers.clear();
        
        // Shutdown other systems
        regenManager.shutdown();
        oreClusterHealthCheck.shutdown();
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
