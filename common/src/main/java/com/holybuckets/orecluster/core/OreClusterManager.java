package com.holybuckets.orecluster.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.LevelSaveData;
import com.holybuckets.foundation.datastructure.ConcurrentLinkedSet;
import com.holybuckets.foundation.datastructure.ConcurrentSet;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.blay09.mods.balm.api.event.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.tuple.Pair;
import oshi.annotation.concurrent.ThreadSafe;

//Java Imports

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.holybuckets.orecluster.core.model.ManagedOreClusterChunk.TEST_ID;
import static com.holybuckets.orecluster.core.model.ManagedOreClusterChunk.isLoaded;
import static java.lang.Thread.sleep;

/**
 * Class: OreClusterManager
 *
 * Description: This class will manage all ore clusters that exist in the instance
 *  - Determines which chunks clusters will appear in
 *  - Determines the type of cluster that will appear
 *  - All variables and methods are static
 *
 *  #Variables - list all variables and a brief description
 *  config - (private) RealTimeConfig object contains statically defined and configurable variables
 *  randSeqClusterPositionGen - (private) Random object for generating cluster positions
 *
 *  newlyLoadedChunks - (private) LinkedBlockingQueue of chunkIds that have been loaded and not yet processed
 *  chunksPendingDeterminations - (private) LinkedBlockingQueue of chunkIds that are pending cluster determination
 *  chunksPendingGeneration - (private) LinkedBlockingQueue of chunkIds that are pending cluster generation
 *
 *  existingClusters - (private) ConcurrentHashMap of <chunkId, <oreType, Vec3i>> containing all existing clusters
 *      in the world, each String chunkId maps to a HashMap of each chunk's cluster type(s) and origin
 *  existingClustersByType - (private) ConcurrentHashMap of <oreType, <chunkId>> containing all existing clusters
 *      allows to check quickly if any newly generated chunk has a nearby cluster of its type
 *  chunksPendingClusterGen - (private) ConcurrentLinkedQueue of chunkIds that are pending cluster generation in the main gamethread
 *
 *  exploredChunks - (private) LinkedHashSet of chunkIds that have been explored
 *  mainSpiral - (private) ChunkGenerationOrderHandler object that generates a spiral of chunkIds
 *
 *  oreClusterCalculator - (private) Handles calculations for cluster determination and generation
 *  managerRunning - (private) boolean flag for toggling internal threads on and off
 *
 *  threadPoolLoadedChunks - (private) ExecutorService for handling newly loaded chunks, 1 thread
 *  threadPoolClusterDetermination - (private) ExecutorService for handling cluster determinations, 1 thread
 *  threadPoolClusterGeneration - (private) ExecutorService for handling cluster generation, 3 threads
 *
 *  #Methods - list all methods and a brief description
 *
 **/


public class OreClusterManager {

    public static final String CLASS_ID = "002";    //value used in logs
    public static final GeneralConfig GENERAL_CONFIG = GeneralConfig.getInstance();
    
    // Worker thread control map
    private static final Map<String, Boolean> WORKER_THREAD_ENABLED = new HashMap<>() {{
        put("workerThreadLoadedChunk", true);
        put("workerThreadDetermineClusters", true);
        put("workerThreadCleanClusters", true);
        put("workerThreadGenerateClusters", true);
        put("workerThreadEditChunk", true);
    }};

    private final Map<String, List<Long>> THREAD_TIMES = new HashMap<>() {{
        put("handleChunkDetermination", new ArrayList<>());
        put("handleChunkCleaning", new ArrayList<>());
        put("handleChunkClusterPreGeneration", new ArrayList<>());
        put("handleChunkManifestation", new ArrayList<>());
    }};

    /** Variables **/
    private Integer LOADS = 0;
    private Integer UNLOADS = 0;
    private final LevelAccessor level;
    private final ModRealTimeConfig config;
    private Random randSeqClusterPositionGen;
    private Random randSeqClusterBuildGen;
    //private Random randSeqClusterShapeGen;


    private final LinkedBlockingQueue<String> chunksPendingHandling;
    private final LinkedBlockingQueue<String> chunksPendingDeterminations;
    private final ConcurrentHashMap<String, ManagedOreClusterChunk> chunksPendingCleaning;
    private final ConcurrentHashMap<String, ManagedOreClusterChunk> chunksPendingPreGeneration;
    //private final ConcurrentHashMap<String, ManagedOreClusterChunk> chunksPendingManifestation;

    //<chunkId, <oreType, Vec3i>>


    private final ConcurrentLinkedSet<String> determinedSourceChunks;
    private final ConcurrentSet<String> determinedChunks;
    private final ConcurrentHashMap<String, ManagedOreClusterChunk> loadedOreClusterChunks;
    private final Set<String> initializedOreClusterChunks;

    private final ConcurrentHashMap<Block, Set<String> > existingClustersByType;
    private final ChunkGenerationOrderHandler mainSpiral;
    private OreClusterCalculator oreClusterCalculator;

    //Threads
    private volatile boolean managerRunning = false;
    private volatile boolean initializing = false;
    private final ConcurrentHashMap<String, Long> threadstarts = new ConcurrentHashMap<>();
    private final Thread threadInitSerializedChunks = new Thread(this::threadInitSerializedChunks);
    private final Thread threadWatchManagedOreChunkLifetime = new Thread(this::threadWatchManagedOreChunkLifetime);

    private final ExecutorService threadPoolLoadedChunks;
    private final ExecutorService threadPoolClusterDetermination;
    private final ThreadPoolExecutor threadPoolClusterCleaning;
    private final ThreadPoolExecutor threadPoolClusterGenerating;
    private final ThreadPoolExecutor threadPoolChunkEditing;
    //private final ThreadPoolExecutor threadPoolChunkProcessing;

    /** Constructor **/
    public OreClusterManager(LevelAccessor level, ModRealTimeConfig config)
    {
        super();
        this.level = level;
        this.config = config;

        this.existingClustersByType = new ConcurrentHashMap<>();
        this.loadedOreClusterChunks = new ConcurrentHashMap<>();
        this.determinedSourceChunks = new ConcurrentLinkedSet<>();
        this.determinedChunks = new ConcurrentSet<>();
        this.chunksPendingHandling = new LinkedBlockingQueue<>();
        this.chunksPendingDeterminations = new LinkedBlockingQueue<>();
        this.chunksPendingCleaning = new ConcurrentHashMap<>();
        this.chunksPendingPreGeneration = new ConcurrentHashMap<>();

        this.initializedOreClusterChunks = new ConcurrentSet<>();
        //this.chunksPendingManifestation = new ConcurrentHashMap<>();

        this.mainSpiral = new ChunkGenerationOrderHandler(null);
        //Thread pool needs to have one thread max, use Synchronous queue and discard policy
        this.threadPoolLoadedChunks = new ThreadPoolExecutor(1, 1, 1L,
         TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
        //Thread pool
        this.threadPoolClusterDetermination = new ThreadPoolExecutor(1, 1,
         30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());

        this.threadPoolClusterCleaning = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());

        this.threadPoolClusterGenerating = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());

        /*
        this.threadPoolChunkProcessing = new ThreadPoolExecutor(1, 1,
            300L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
         */

        this.threadPoolChunkEditing = new ThreadPoolExecutor(1, 1,
            300L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());


        init(level);
        LoggerProject.logInit("002000", this.getClass().getName());
    }

    /** Get Methods **/
    public ModRealTimeConfig getConfig() {
        return config;
    }

    public Map<String, ManagedOreClusterChunk> getLoadedOreClusterChunks() {
        return loadedOreClusterChunks;
    }

    public ConcurrentHashMap<Block, Set<String>> getExistingClustersByType() {
        return existingClustersByType;
    }





    /** Behavior **/
    public void init(LevelAccessor level)
    {

        if (ModRealTimeConfig.CLUSTER_SEED == null)
            ModRealTimeConfig.CLUSTER_SEED = GENERAL_CONFIG.getWorldSeed();
        long seed = ModRealTimeConfig.CLUSTER_SEED;
        this.randSeqClusterPositionGen = new Random(seed);
        //this.randSeqClusterBuildGen = new Random(seed);

        this.oreClusterCalculator = new OreClusterCalculator( this );

        config.getOreConfigs().forEach((oreType, oreConfig) ->
            existingClustersByType.put(oreType, new ConcurrentSet<>() )
        );

       this.threadInitSerializedChunks.start();
       //this.threadWatchManagedOreChunkLifetime.start();

       EventRegistrar.getInstance().registerOnDataSave(this::save, EventPriority.High);
    }

    /**
     * Description: Sweeps loaded ore cluster chunks for any that
     * have been loaded for more than 300s and are still in DETERMINED status
     * @param chunk
     * chunkLifetime
     * chunkDelete
     * chunkExpire
     */
     private static final Long MAX_DETERMINED_CHUNK_LIFETIME_MILLIS = 150_000L;
     private static final Long SLEEP_TIME_MILLIS = 30_000L;
    private void threadWatchManagedOreChunkLifetime()
    {
        try {
            while(managerRunning)
            {
                if( loadedOreClusterChunks.isEmpty() )
                {
                    sleep(10);
                    continue;
                }

                //1. Update time loaded for all currently loaded chunks
                loadedOreClusterChunks.values().stream()
                    .filter(ManagedOreClusterChunk::isLoaded)
                    .forEach(ManagedOreClusterChunk::updateTimeLoaded);


                Long time = System.currentTimeMillis();
                List<String> expired_chunks = new ArrayList<>();
                loadedOreClusterChunks.values().stream()
                    .filter(c -> !isLoaded(c))
                    .filter(c -> time - c.getTickLoaded() > MAX_DETERMINED_CHUNK_LIFETIME_MILLIS)
                    .forEach(c -> expired_chunks.add(c.getId()));

                for( String id : expired_chunks ) {
                    loadedOreClusterChunks.remove(id);
                }

                sleep(SLEEP_TIME_MILLIS);
            }
        }
        catch (Exception e) {
            LoggerProject.logError("002002", "Error in threadWatchManagedOreChunkLifetime: " + e.getMessage());
        }

    }


    /**
     * Description: Adds a chunk to the loadedOreClusterChunks map if it does not exist, called when a chunk is deserialized or chunkLoaded.
     * Called before handleLoadedChunkId
     * @param managedChunk
     */
    public void addOrUpdatedLoadedChunk(ManagedOreClusterChunk managedChunk)
    {
        String chunkId = managedChunk.getId();
        loadedOreClusterChunks.put(chunkId, managedChunk.getEarliest(loadedOreClusterChunks));
        chunksPendingHandling.add(managedChunk.getId());
        threadPoolLoadedChunks.submit(this::workerThreadLoadedChunk);
        threadPoolChunkEditing.submit(this::workerThreadEditChunk);

        //LoggerProject.logInfo("002001", "Chunk " + chunkId + " added to queue size " + chunksPendingHandling.size());
    }

    /**
     * Description: Handles the id of any recently loaded chunk. If the chunkId has not had a ManagedOreClusterChunk created for it, one is created.
     * Otherwise, the chunk is processed according to its status.
     * @param chunkId
     */
    public void handleLoadedChunkId(String chunkId)
    {
        this.LOADS++;
        loadedOreClusterChunks.putIfAbsent(chunkId, ManagedOreClusterChunk.getInstance(this.level, chunkId));
        chunksPendingHandling.add(chunkId);
        threadPoolLoadedChunks.submit(this::workerThreadLoadedChunk);
        threadPoolChunkEditing.submit(this::workerThreadEditChunk);
    }

    /**
     * Description: Handles newly unloaded chunks
     * @param chunk
     */
    public void handleChunkUnloaded(ChunkAccess chunk)
    {
        String chunkId = ChunkUtil.getId(chunk);
        this.UNLOADS++;
    }

    /**
     * Newly loaded chunks are polled in a queue awaiting batch handling
     * If the chunk has already been processed it is skipped
     */
    private void workerThreadLoadedChunk()
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadLoadedChunk")) {
            return;
        }
        threadstarts.put("workerThreadLoadedChunk", System.currentTimeMillis());
        try
        {
            while( !chunksPendingHandling.isEmpty() )
            {
                if( this.initializing ) {
                    sleep(10);
                    continue;
                }

                String chunkId = chunksPendingHandling.poll(1, TimeUnit.SECONDS);

                if( chunkId == null )
                    continue;

                long start = System.nanoTime();
                handleLoadedChunk(chunkId);
                long end = System.nanoTime();
                //Remove duplicates
                chunksPendingHandling.remove(chunkId);
            }
        }
        catch (InterruptedException e)
        {
            LoggerProject.logError("002003","OreClusterManager::onNewlyAddedChunk() thread interrupted: "
                 + e.getMessage());
        }

    }

    /**
     * Handle newly loaded chunk
     *
     * 1. If this chunkId exists in existingClusters, check regen
     * 2. If the chunkId exists in exploredChunks, ignore
     * 3. If the chunkId does not exist in exploredChunks, queue a batch
     *
     */
    private void handleLoadedChunk(String chunkId)
    {
        if( chunkId.equals(TEST_ID) ) {
            int i = 0;
        }

        ManagedOreClusterChunk chunk = loadedOreClusterChunks.get(chunkId);
        if( chunk == null )
        {
            chunk = ManagedOreClusterChunk.getInstance(this.level, chunkId);
            loadedOreClusterChunks.put(chunkId, chunk);
            if( determinedChunks.contains(chunkId) ) {
                chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.DETERMINED);
            }
            handleLoadedChunk(chunkId);
        }
        else if( ManagedOreClusterChunk.isNoStatus(chunk) )
        {
            if(determinedChunks.contains(chunkId)) {
                chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.DETERMINED);
                handleLoadedChunk(chunkId);
                return;
            }
            chunksPendingDeterminations.add(chunkId);
            this.threadPoolClusterDetermination.submit(this::workerThreadDetermineClusters);
        }
        else if( ManagedOreClusterChunk.isDetermined(chunk) ) {
            if(chunk.hasClusters()) {
                chunk.getClusterTypes().forEach((oreType, pos) -> {
                    existingClustersByType.get(oreType).add(chunkId);
                });
            }
            chunksPendingCleaning.put(chunkId, chunk);
            this.threadPoolClusterCleaning.submit(this::workerThreadCleanClusters);
        }
        else if( ManagedOreClusterChunk.isCleaned(chunk) )
        {
            //LoggerProject.logDebug("002007","Chunk " + chunkId + " has been cleaned");
            if( chunk.hasClusters() )
            {
                chunksPendingPreGeneration.put(chunkId, chunk);
                this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
            }
        }
        else if( ManagedOreClusterChunk.isRegenerated(chunk) )
        {
            //LoggerProject.logDebug("002007","Chunk " + chunkId + " has been regenerated");
            if( !chunk.hasBlockUpdates() )
            {
                chunksPendingPreGeneration.put(chunkId, chunk);
                this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
            }

        }
        else if( ManagedOreClusterChunk.isGenerated(chunk) )
        {
            //LoggerProject.logDebug("002008","Chunk " + chunkId + " has been generated");
            //chunksPendingManifestation.add(chunkId);
        }
        else if( ManagedOreClusterChunk.isComplete(chunk) )
        {
            //LoggerProject.logDebug("002009","Chunk " + chunkId + " is complete");
            loadedOreClusterChunks.remove(chunkId);
        }

    }

    private void workerThreadDetermineClusters() 
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadDetermineClusters")) {
            return;
        }
        threadstarts.put("workerThreadDetermineClusters", System.currentTimeMillis());
        Throwable thrown = null;
        if(!this.managerRunning)
            return;

        try
        {
            while( this.managerRunning )
            {

                if( chunksPendingDeterminations.size() == 0 ) {
                    sleep(10);
                    continue;
                }
                String chunkId = chunksPendingDeterminations.poll();
                
                while( !this.determinedChunks.contains(chunkId) ) {
                    long start = System.nanoTime();
                    handleChunkDetermination(ModRealTimeConfig.ORE_CLUSTER_DTRM_BATCH_SIZE_TOTAL, chunkId);
                    long end = System.nanoTime();
                    THREAD_TIMES.get("handleChunkDetermination").add((end - start) / 1_000_000);
                    this.threadPoolClusterCleaning.submit(this::workerThreadCleanClusters);
                }

                //MAX
                LoggerProject.logDebug("002020", "workerThreadDetermineClusters, after handleChunkDetermination for chunkId: " + chunkId);
            }

        }
        catch (Exception e) {
            thrown = e;
            LoggerProject.logError("002011.1","Error in workerThreadDetermineClusters: " + e.getMessage());
        }
        finally {
            LoggerProject.threadExited("002011",this, thrown);
        }
    }

    /**
     * Description: Polls determinedChunks attempts to clean any chunk and
     * adds any cluster chunk to the chunksPendingGeneration queue. If chunk
     * is not loaded at the time it is polled, it is skipped and re-added to the queue.
     *
     * 0. Get iterable list of all determined chunks, filter by status == Determined
     * 1. Get next determined chunkId
     * 2. Determine cluster is loaded
     *
     * 3. Thread the chunk cleaning process, low priority, same executor as cluster generation
     * 4. handleChunkCleaning will add the chunk to chunksPendingGeneration once finished
     */
    private void workerThreadCleanClusters()
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadCleanClusters")) {
            return;
        }
        threadstarts.put("workerThreadCleanClusters", System.currentTimeMillis());
        Throwable thrown = null;
        if(!managerRunning)
            return;


        try
        {
            while( managerRunning )
            {

                Queue<ManagedOreClusterChunk> chunksToClean = chunksPendingCleaning.values().stream()
                    .filter(c -> c.hasChunk())
                    .collect(Collectors.toCollection(LinkedList::new));

                if( chunksToClean.size() == 0 ) {
                    sleep(10);
                    continue;
                }
                LoggerProject.logDebug("002026", "workerThreadCleanClusters cleaning chunks: " + chunksToClean.size());

                for (ManagedOreClusterChunk chunk : chunksToClean)
                {
                        try {
                            long start = System.nanoTime();
                            editManagedChunk(chunk, this::handleChunkCleaning);
                            long end = System.nanoTime();
                            THREAD_TIMES.get("handleChunkCleaning").add((end - start) / 1_000_000);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            LoggerProject.logError("002030","Error cleaning chunk: " + chunk.getId() + " message: "  );
                        }
                }

            }
            //END WHILE MANAGER RUNNING

        }
        catch (Exception e) {
            thrown = e;
        }
        finally {
            LoggerProject.threadExited("002028",this, thrown);
        }
    }


    /**
     * Description: Polls prepared chunks from chunksPendingGenerationQueue
     */
    private void workerThreadGenerateClusters()
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadGenerateClusters")) {
            return;
        }
        threadstarts.put("workerThreadGenerateClusters", System.currentTimeMillis());
        Throwable thrown = null;
        if(!managerRunning)
            return;

        final Predicate<ManagedOreClusterChunk> IS_ADJACENT_CHUNKS_LOADED = chunk -> {
            ChunkPos pos = HBUtil.ChunkUtil.getPos(chunk.getId());
            //give me a nested for loop over x, z coordinates from -1 to 1
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkPos adjPos = new ChunkPos(pos.x + x, pos.z + z);
                    if (!loadedOreClusterChunks.containsKey(ChunkUtil.getId(adjPos)))
                        return false;
                }
            }
            return true;
        };

        try
        {

            while( managerRunning )
            {
                Queue<ManagedOreClusterChunk> chunksToGenerate = chunksPendingPreGeneration.values().stream()
                    .filter(c -> c.hasReadyClusters())
                    .collect(Collectors.toCollection(LinkedList::new));

                if( chunksToGenerate.size() == 0 ) {
                    sleep(10);
                    continue;
                }

                for (ManagedOreClusterChunk chunk : chunksToGenerate)
                {
                    long start = System.nanoTime();
                    editManagedChunk(chunk, this::handleChunkClusterPreGeneration);
                    long end = System.nanoTime();
                    THREAD_TIMES.get("handleChunkClusterPreGeneration").add((end - start) / 1_000_000);

                    if( ManagedOreClusterChunk.isPregenerated(chunk) ) {
                        chunksPendingPreGeneration.remove(chunk.getId());
                    }
                }


            }

        }
        catch (Exception e) {
            thrown = e;
        }
        finally {
            LoggerProject.threadExited("002007",this, thrown);
        }
    }
    //END workerThreadGenerateClusters

    private void workerThreadEditChunk()
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadEditChunk")) {
            return;
        }
        threadstarts.put("workerThreadEditChunk", System.currentTimeMillis());
        Throwable thrown = null;
        if(!managerRunning)
            return;

        try
        {

            while( managerRunning )
            {
                //sleep(1000);
                //Sleep if loaded chunks is empty, else iterate over them
                if( loadedOreClusterChunks.isEmpty() )
                {
                    sleep(10);
                    continue;
                }

                if( loadedOreClusterChunks.containsKey(TEST_ID)
                && ManagedOreClusterChunk.isPregenerated(loadedOreClusterChunks.get(TEST_ID)) )
                {
                    int i = 0;
                }

                handleChunkReadiness();

                List<ManagedOreClusterChunk> readyChunks = loadedOreClusterChunks.values().stream()
                    .filter(c -> c.isReady())
                    .toList();


                for( ManagedOreClusterChunk chunk : readyChunks )
                {
                    if( chunk.getId().equals(TEST_ID) ) {
                         int i = 0;
                    }

                    //Sometimes things get through
                    if( !chunk.isReady() || !chunk.hasChunk() )
                        continue;

                    long start = System.nanoTime();
                    editManagedChunk(chunk, this::handleChunkManifestation);
                    long end = System.nanoTime();
                    THREAD_TIMES.get("handleChunkManifestation").add((end - start) / 1_000_000);

                }

                //sleep(10000);   //10 seconds
            }

        }
        catch (Exception e) {
            thrown = e;
        }
        finally {
            LoggerProject.threadExited("002031",this, thrown);
        }
    }


    private void handleChunkReadiness()
    {
        Throwable thrown = null;
        try
        {
            if( true ) {
                //sleep(10000);
                //return;
            }
            //sleep(1000);

            //while( managerRunning )
            {


                List<ManagedOreClusterChunk> availableChunks = loadedOreClusterChunks.values().stream()
                    .filter(c -> c.hasChunk() )
                    .filter(c -> !c.isReady())
                    .toList();

                //Cleaned chunks that have not been harvested yet
                List<ManagedOreClusterChunk> cleanedChunks = availableChunks.stream()
                    .filter(c -> ManagedOreClusterChunk.isCleaned(c) )
                    .filter(c -> !c.hasClusters())              //If it still needs to generate clusters, skip
                    .filter(c -> !c.checkClusterHarvested())    //Checks if cluster has been interacted with by player
                    .toList();

                 //Pre-generated chunks that have not been harvested yet
                List<ManagedOreClusterChunk> preGeneratedChunks = availableChunks.stream()
                    .filter(c -> ManagedOreClusterChunk.isPregenerated(c) )
                    .filter(c -> !c.checkClusterHarvested())                //Checks if cluster has been interacted with by player
                    .toList();

                //Any adjacentChunks to the player that are not harvested
                /*
                final List<ChunkAccess> PLAYER_CHUNKS = GeneralConfig.getPlayers();
                //Get location of each player
                //Get chunk at each location
                //Determine adjacent chunks
                //Put adjacent chunks into an array
                //Filter all chunks that have been harvested
                List<ManagedOreClusterChunk> adjacentChunks = loadedOreClusterChunks.values().stream()
                    .filter(c -> ManagedOreClusterChunk.isCleaned(c) )
                    .filter(c -> !c.isReady())
                    .filter(c -> !c.checkClusterHarvested())    //Checks if cluster has been interacted with by player
                    .filter(c -> c.hasChunk())                 //must have loaded chunk
                    .toList();

                */

                //Join Lists and mark as ready
                List<ManagedOreClusterChunk> readyChunks = new ArrayList<>();
                readyChunks.addAll(cleanedChunks);
                readyChunks.addAll(preGeneratedChunks);
                //readyChunks.addAll(adjacentChunks);

                readyChunks.forEach(c -> editManagedChunk(c, ch -> ch.setReady(true)) );
            }

        }
        catch (Exception e) {
            thrown = e;
            LoggerProject.logError("002031.1","Error in handleChunkReadiness: " + e.getMessage());
        }

    }


    /**
     * Batch process that determines the location of clusters in the next n chunks
     * @param batchSize
     * @param chunkId
     *
     * handleChunkDetermination
     * handleDetermineChunks
     */
    private void handleChunkDetermination(int batchSize, String chunkId) 
    {

        LoggerProject.logDebug("002008", "Queued " + chunkId + " for cluster determination");
        determinedSourceChunks.add(chunkId);
        LinkedHashSet<String> chunkIds = getBatchedChunkList(batchSize, chunkId);
        //long step1Time = System.nanoTime();

        //Map<ChunkId, Clusters>
        Map<String, List<Block>> clusters;
        clusters = oreClusterCalculator.calculateClusterLocations(chunkIds.stream().toList() , randSeqClusterPositionGen);
        //long step2Time = System.nanoTime();

        // #3. Add clusters to determinedClusters
        for( String id: chunkIds)
        {
            if( id.equals(TEST_ID)) {
                int i = 0;
            }
        //Create clusters for chunks that aren't loaded yet
            ManagedOreClusterChunk chunk = loadedOreClusterChunks.getOrDefault(id, ManagedOreClusterChunk.getInstance(level, id) );
            this.loadedOreClusterChunks.put(id, chunk);

            chunk.addClusterTypes(clusters.get(id));
            chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.DETERMINED);
            this.chunksPendingCleaning.put(id, chunk);
            this.determinedChunks.add(id);
        }
        LoggerProject.logDebug("002010","Added " + clusters.size() + " clusters to determinedChunks");


        // #4. Add clusters to existingClustersByType
        for( Map.Entry<String, List<Block>> clusterChunk : clusters.entrySet())
        {
            for( Block clusterOreType : clusterChunk.getValue() )
            {
                existingClustersByType.get(clusterOreType).add(clusterChunk.getKey());
            }
        }


        long end = System.nanoTime();
        //LoggerProject.logDebug("handlePrepareNewCluster #4  " + LoggerProject.getTime(step3Time, end) + " ms");
    }

    /**
     * Step 2. Cleans the chunk by performing 3 distinct operations
     * 1. Scan the chunk for all cleanable ores
     * 2. Determine the cluster position for each ore in the managed chunk
     * 3. Determine which Ores need to be cleaned based on Ore Config data
     *
     * handleCleanClusters
     * handleChunkCleaning
     * @param chunk
     */
     private static int totalCleaned = 0;
     private static int missingOriginalsCleaned = 0;
    private void handleChunkCleaning(ManagedOreClusterChunk chunk)
    {

        if( chunk == null|| chunk.getChunk(false) == null )
            return;

        if( !chunk.testChunkStatusOrAfter(ChunkStatus.FULL ) ) return;

        LoggerProject.logDebug("002025", "Cleaning chunk: " + chunk.getId());

        try {

            //0. Add a gold_block to blockStateUpdates
            if(true)
            {
                LevelChunk c = chunk.getChunk(false);
                if( c == null ) return;
                BlockPos pos = c.getPos().getWorldPosition();
                final BlockPos constPos = pos.offset(0, 128, 0);
                chunk.addBlockStateUpdate(Blocks.GOLD_BLOCK, new BlockPos(pos.getX(), 128, pos.getZ()));
                Map<Block, BlockPos> clusterTypes = chunk.getClusterTypes();
                clusterTypes.replaceAll((oreType, sourcePos) -> {
                    return new BlockPos(constPos.getX(), 128, constPos.getZ());
                });
            }

            final Map<Block, OreClusterConfigModel> ORE_CONFIGS = config.getOreConfigs();

            final Set<Block> COUNTABLE_ORES = ORE_CONFIGS.keySet().stream().collect(Collectors.toSet());
            final Set<Block> CLEANABLE_ORES = ORE_CONFIGS.keySet().stream().filter(oreName -> {
                return ORE_CONFIGS.get(oreName).oreVeinModifier < 1.0f;
            }).collect(Collectors.toSet());

            //No ores to clean, no clusters to build
            if( COUNTABLE_ORES.isEmpty() && !chunk.hasClusters() )
            {
                chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.CLEANED);
                chunksPendingCleaning.remove(chunk.getId());
                return;
            }


            //1. Scan chunk for all cleanable ores, testing each block
            if( chunk.getOriginalOres() == null )
            {
                boolean isSuccessful = oreClusterCalculator.cleanChunkFindAllOres(chunk, COUNTABLE_ORES);
                if( !isSuccessful )
                    return;
                missingOriginalsCleaned++;
            }
            totalCleaned++;

            //2. Determine the cluster position for each ore in the managed chunk
            if( chunk.hasClusters() )
            {
                oreClusterCalculator.cleanChunkSelectClusterPosition(chunk);
                this.chunksPendingPreGeneration.put(chunk.getId(), chunk);
                this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
            }

            //3. Cleans chunk of all ores discovered in the findAllOres method and stored in chunk.getOriginalOres()
            //oreClusterCalculator.cleanChunkOres(chunk, CLEANABLE_ORES);

            //4. Set the originalOres array to null to free up memory
            chunk.setOriginalOres(null);

            //5. Set the chunk status to CLEANED

            chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.CLEANED);
            chunksPendingCleaning.remove(chunk.getId());
            //initializedOreClusterChunks.add(chunk.getId());


            LoggerProject.logInfo("002027", "Cleaning chunk: " + chunk.getId() + " complete");
    }
    catch(Exception e) {
        StringBuilder error = new StringBuilder();
        error.append("Error cleaning chunk: ");
        error.append(chunk.getId());
        error.append(" name | message: ");
        error.append(e.getClass());
        error.append(" | ");
        error.append(e.getMessage());
        error.append(" stacktrace: \n");
        error.append(Arrays.stream(e.getStackTrace()).toList().toString());
        LoggerProject.logError("002027.1", error.toString());
    }

    }
    //END handleCleanClusters


    /**
     * Takes a ManagedOreClusterChunk and generates a sequence of positions
     * that will become the ore cluster in the world. These positions are
     * added to ManagedOreClusterChunk::blockStateUpdates
     *
     * @param chunk
     * handleChunkClusterGeneration
     * clusterGeneration
     * clusterPregeneration
     * generation
     */
    private void handleChunkClusterPreGeneration(ManagedOreClusterChunk chunk)
    {
        
        if( chunk == null || chunk.getChunk(false) == null )
            return;

        if(chunk.getClusterTypes() == null || chunk.getClusterTypes().size() == 0)
            return;



        LoggerProject.logDebug("002015","Generating clusters for chunk: " + chunk.getId());

        String SKIPPED = null;
        for( Block oreType : chunk.getClusterTypes().keySet() )
        {
            //1. Get the cluster config

            BlockPos sourcePos = chunk.getClusterTypes().get(oreType);
            if( sourcePos == null ) {
                LoggerProject.logDebug("002016","No source position for oreType: " + oreType);
                SKIPPED = BlockUtil.blockToString(oreType);
                continue;
            }


            List<BlockPos> clusterPos = oreClusterCalculator.generateCluster(Pair.of(oreType, sourcePos));
            clusterPos.forEach( pos -> chunk.addBlockStateUpdate(oreType, pos) );
        }

        if( SKIPPED == null )
            chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.PREGENERATED);
    }

    /**
     * Alters the chunk to place blocks in the world as necessary to build clusters or reduce
     *
     * @param chunk
     * doEdit
     * editChunk
     * updateChunkBlocks
     * updateBlockStates
     */
    private void handleChunkManifestation(ManagedOreClusterChunk chunk)
    {
        //LoggerProject.logDebug("002033","Editing chunk: " + chunk.getId());

        if( chunk.getId().equals(TEST_ID) ) {
            int i = 0;
        }

        boolean isSuccessful = false;
        if( !chunk.hasBlockUpdates() ) {
            isSuccessful = true;
        }
        else
        {
            LevelChunk levelChunk = chunk.getChunk(false);
            if (levelChunk == null) return;
            Queue<Pair<Block, BlockPos>> updates = chunk.getBlockStateUpdates();
            isSuccessful = ManagedChunk.updateChunkBlocks(levelChunk.getLevel(), updates.stream().toList() );
        }

        if( isSuccessful )
        {
            chunk.setReady(false);
            chunk.getBlockStateUpdates().clear();
            chunk.setOriginalOres(null);

            if( chunk.hasClusters() ) {
                chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.GENERATED);
            }
            else {
                chunk.setStatus(ManagedOreClusterChunk.ClusterStatus.COMPLETE);
                loadedOreClusterChunks.remove(chunk.getId());
            }
        }
    }


    private void threadInitSerializedChunks()
    {
        this.managerRunning = false;
        this.initializing = true;
        DataStore ds = GeneralConfig.getInstance().getDataStore();
        LevelSaveData levelData = ds.getOrCreateLevelSaveData(OreClustersAndRegenMain.MODID, level);

        if( levelData.get("determinedSourceChunks") == null ) {
            this.initializing = false;
            this.managerRunning = true;
            return;
        }

        //initializedOreClusterChunks.addAll(ManagedChunkUtilityAccessor.getInitializedChunks(level));

        JsonArray ids = levelData.get("determinedSourceChunks").getAsJsonArray();
        List<String> chunkIds = ids.asList().stream().map(JsonElement::getAsString).toList();
        Long start = System.nanoTime();
        for( String id : chunkIds)
        {
            ChunkPos pos = HBUtil.ChunkUtil.getPos(id);
            HBUtil.ChunkUtil.getLevelChunk(level, pos.x, pos.z, true);
            while( !this.determinedChunks.contains(id) )
            {
                try {
                    handleChunkInitialization(id);
                } catch (Exception e) {
                    LoggerProject.logError("002001.1", "Error in threadInitSerializedChunks, continuing: " + e.getMessage());
                }
            }

        }
        Long end = System.nanoTime();
        THREAD_TIMES.get("handleChunkDetermination").add((end - start) / 1_000_000); // Convert to milliseconds

        this.loadedOreClusterChunks.clear();

        this.initializing = false;
        this.managerRunning = true;
    }

    /**
     * Initializes chunks on server RESTART - does not trigger when world first loaded.
     * essentially redetermines all chunks in the same order there were originally
     * @param chunkId
     */
    private void handleChunkInitialization(String chunkId)
    {
        int batchSize = ModRealTimeConfig.ORE_CLUSTER_DTRM_BATCH_SIZE_TOTAL;
        determinedSourceChunks.add(chunkId);
        LinkedHashSet<String> chunkIds = getBatchedChunkList(batchSize, chunkId);

        //Map<ChunkId, Clusters>
        Map<String, List<Block>> clusters;
        clusters = oreClusterCalculator.calculateClusterLocations(chunkIds.stream().toList() , randSeqClusterPositionGen);

        // #3. Add clusters to determinedClusters
        for( String id: chunkIds) {
            this.determinedChunks.add(id);
        }

        // #4. Add clusters to existingClustersByType
        for( Map.Entry<String, List<Block>> clusterChunk : clusters.entrySet()) {
            for( Block clusterOreType : clusterChunk.getValue() ) {
                existingClustersByType.get(clusterOreType).add(clusterChunk.getKey());
            }
        }
    }



    /**
     *              UTILITY SECTION
     */


    /**
     * Batch process that determines the location of clusters in the next n chunks
     * Chunk cluster determinations are made spirally from the 'start' chunk, up, right, down, left
     */
    private LinkedHashSet<String> getBatchedChunkList(int batchSize, String startId)
    {
        LinkedHashSet<String> chunkIds = new LinkedHashSet<>();
        ChunkPos pos = HBUtil.ChunkUtil.getPos(startId);
        ChunkGenerationOrderHandler chunkIdGeneratorHandler = mainSpiral;
        if (chunksPendingCleaning.size() > Math.pow(ModRealTimeConfig.ORE_CLUSTER_DTRM_RADIUS_STRATEGY_CHANGE, 2)) {
            chunkIdGeneratorHandler = new ChunkGenerationOrderHandler(pos);
        }

        for (int i = 0; i < batchSize; i++) {
            ChunkPos next = chunkIdGeneratorHandler.getNextSpiralChunk();
            chunkIds.add(ChunkUtil.getId(next));
        }

        return chunkIds;
    }

    /** GETTERS AND SETTERS **/

        /** GETTERS **/

        public ManagedOreClusterChunk getLoadedChunk(String chunkId) {
            return loadedOreClusterChunks.get(chunkId);
        }

        /** SETTERS **/



    /**
     * If the main spiral is still being explored (within 256x256 chunks of worldspawn)
     * then we return all explored chunks, otherwise we generate a new spiral with the requested area
     * at the requested chunk
     * @param start
     * @param spiralArea
     * @return LinkedHashSet of chunkIds that were recently explored
     */
    public LinkedHashSet<String> getRecentChunkIds(ChunkPos start, int spiralArea)
    {
        if (chunksPendingCleaning.size() < Math.pow(ModRealTimeConfig.ORE_CLUSTER_DTRM_RADIUS_STRATEGY_CHANGE, 2)) {
            return chunksPendingCleaning.values().stream().map(ManagedOreClusterChunk::getId).
            collect(Collectors.toCollection(LinkedHashSet::new));
        }
        else
        {
            LinkedHashSet<String> chunkIds = new LinkedHashSet<>();
            ChunkGenerationOrderHandler spiralHandler = new ChunkGenerationOrderHandler(start);

            try
            {
                for (int i = 0; i < spiralArea; i++) {
                    ChunkPos next = spiralHandler.getNextSpiralChunk();
                    chunkIds.add( ChunkUtil.getId(next) );
                }

            } catch (Exception e) {
                LoggerProject.logError("002016","Error generating spiral chunk ids at startPos: " + start.toString() + " message " + e.getMessage());
            }

            return chunkIds;
        }
    }

    /**
     * Edits a ManagedChunk object from determinedChunks with a consumer, ensuring each object is edited atomically
     * Returns an empty optional if the chunk is locked or null is passed
     * @param chunk
     * @param consumer
     * @return
     */
    @ThreadSafe
    private synchronized Optional<ManagedOreClusterChunk> editManagedChunk(ManagedOreClusterChunk chunk, Consumer<ManagedOreClusterChunk> consumer)
    {
        if (chunk == null)
            return Optional.empty();

        if( chunk.getLock().isLocked() )
            return Optional.empty();

        chunk.getLock().lock();
        consumer.accept(chunk);
        chunk.getLock().unlock();

        return Optional.ofNullable(chunk);
    }



    public void shutdown()
    {
        this.save();

        managerRunning = false;

        threadPoolLoadedChunks.shutdown();
        threadPoolClusterDetermination.shutdown();
        threadPoolClusterCleaning.shutdown();
        threadPoolClusterGenerating.shutdown();
        threadPoolChunkEditing.shutdown();
        threadInitSerializedChunks.interrupt();
        threadWatchManagedOreChunkLifetime.interrupt();

    }

    /**
     * Description: Saves the determinedSourceChunks to levelSavedata
      */
    private void save()
    {
        //Create new Mod Datastore, if one does not exist for this mod,
        //read determinedSourceChunks into an array and save it to levelSavedata
        DataStore ds = GeneralConfig.getInstance().getDataStore();
        if (ds == null)
            return;
        LevelSaveData levelData = ds.getOrCreateLevelSaveData(OreClustersAndRegenMain.MODID, level);
        String[] ids = determinedSourceChunks.toArray(new String[0]);
        levelData.addProperty("determinedSourceChunks", HBUtil.FileIO.arrayToJson(ids));

    }

    /** STATIC METHODS **/

    public static void onChunkLoad(ChunkLoadingEvent.Load event)
    {
        LevelAccessor level = event.getLevel();
        if( level == null || level.isClientSide() )
            return;

        OreClusterManager manager = OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get( level );
        if( manager != null ) {
            manager.handleLoadedChunkId( HBUtil.ChunkUtil.getId(event.getChunk())  );
        }
    }
    //END onChunkLoad

    public static void addManagedOreClusterChunk(ManagedOreClusterChunk managedChunk )
    {
        OreClusterManager manager = OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get( managedChunk.getLevel() );
        if( manager != null )
        {
            manager.addOrUpdatedLoadedChunk(managedChunk);
        }
    }

    public static void onChunkUnload(ChunkLoadingEvent.Unload event)
    {
        LevelAccessor level = event.getLevel();
        if( level !=null && level.isClientSide() ) {
            //Client side
        }
        else {
            OreClusterManager manager = OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get( level );
            if( manager != null ) {
                manager.handleChunkUnloaded(event.getChunk());
            }
        }
    }

    public static ManagedOreClusterChunk getManagedOreClusterChunk(LevelAccessor level, LevelChunk chunk) {
        OreClusterManager manager = OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get(level);
        if( manager == null ) return null;
        return manager.getManagedOreClusterChunk(chunk);
    }

    public static OreClusterManager getManager(LevelAccessor level) {
        return OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get(level);
    }

    public Set<String> getDeterminedChunks() {
        return this.determinedChunks;
    }

    public ManagedOreClusterChunk getManagedOreClusterChunk(ChunkAccess chunk) {
        return this.getManagedOreClusterChunk(ChunkUtil.getId(chunk));
    }

    public ManagedOreClusterChunk getManagedOreClusterChunk(String chunkId) {
        return this.loadedOreClusterChunks.get(chunkId);
    }

    public ManagedOreClusterChunk getDeterminedOreClusterChunk(ChunkAccess chunk) {
        return this.getDeterminedOreClusterChunk(ChunkUtil.getId(chunk));
    }

    public ManagedOreClusterChunk getDeterminedOreClusterChunk(String chunkId) {
        return this.chunksPendingCleaning.get(chunkId);
    }


    /** ############### **/


    private class ChunkGenerationOrderHandler
    {
        private static final int[] UP = new int[]{0, 1};
        private static final int[] RIGHT = new int[]{1, 0};
        private static final int[] DOWN = new int[]{0, -1};
        private static final int[] LEFT = new int[]{-1, 0};
        private static final int[][] DIRECTIONS = new int[][]{UP, RIGHT, DOWN, LEFT};

        private ChunkPos currentPos;
        private final LinkedHashSet<ChunkPos> sequence;
        private int count;
        private int dirCount;
        private int[] dir;

        public ChunkGenerationOrderHandler(ChunkPos start)
        {
            this.currentPos = (start == null) ? new ChunkPos(0, 0) : start;
            this.sequence = new LinkedHashSet<>();
            this.count = 1;
            this.dirCount = 0;
            this.dir = UP;
        }

        public ChunkPos getNextSpiralChunk()
        {
            if(sequence.size() == 0)
            {
                sequence.add(currentPos);
                return currentPos;
            }

            //This algorithm skips 1,0, fix it

            if (dirCount == count) {
                dir = getNextDirection();
                dirCount = 0;
                if (dir == UP || dir == DOWN) {
                    count++;
                }
            }

            currentPos = ChunkUtil.posAdd(currentPos, dir);
            sequence.add(currentPos);
            dirCount++;

            return currentPos;
        }

        private int[] getNextDirection() {
            int index = Arrays.asList(DIRECTIONS).indexOf(dir);
            return DIRECTIONS[(index + 1) % DIRECTIONS.length];
        }
    }
}
