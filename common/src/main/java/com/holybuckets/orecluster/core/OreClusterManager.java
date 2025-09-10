package com.holybuckets.orecluster.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastore.LevelSaveData;
import com.holybuckets.foundation.datastructure.ConcurrentLinkedSet;
import com.holybuckets.foundation.datastructure.ConcurrentSet;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.orecluster.Constants;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.tuple.Pair;
import oshi.annotation.concurrent.ThreadSafe;

//Java Imports

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.holybuckets.orecluster.OreClustersAndRegenMain.DEBUG;
import static com.holybuckets.orecluster.core.model.ManagedOreClusterChunk.*;
import static java.lang.Thread.sleep;
import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

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
 *  existingClusters - (private) ConcurrentHashMap of (chunkId, (oreType, Vec3i)) containing all existing clusters
 *      in the world, each String chunkId maps to a HashMap of each chunk's cluster type(s) and origin
 *  existingClustersByType - (private) ConcurrentHashMap of (oreType, (chunkId)) containing all existing clusters
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
    public static Map<LevelAccessor, OreClusterManager> MANAGERS;
    private final ManagedChunkUtility chunkUtil;
    
    // Worker thread control map, workerThreadEnabled, off/on,
    private static final Map<String, Boolean> WORKER_THREAD_ENABLED = new HashMap<>() {{
        put("workerThreadLoadedChunk", true);
        put("workerThreadDetermineClusters", true);
        put("workerThreadCleanClusters", true);
        put("workerThreadGenerateClusters", true);
        put("workerThreadEditChunk", true);
    }};

    private void setOffWorkerThreads(String threadName) {
        WORKER_THREAD_ENABLED.put(threadName, false);
    }

    //NEED TO CLEAR TO RETAIN MEMORY
    final Map<String, List<Long>> THREAD_TIMES = new ConcurrentHashMap<>() {{
        put("workerThreadLoadedChunk", new ArrayList<>());
        put("handleChunkInitialization", new ArrayList<>());
        put("handleChunkDetermination", new ArrayList<>());
        put("handleChunkCleaning", new ArrayList<>());
        put("handleChunkClusterPreGeneration", new ArrayList<>());
        put("handleChunkManifestation", new ArrayList<>());
    }};

    /** Variables **/
    private Integer LOADS = 0;
    private Integer UNLOADS = 0;
    private final Level level;
    private final ModRealTimeConfig config;
    private Random randSeqClusterPositionGen;
    private Random randSeqClusterBuildGen;
    //private Random randSeqClusterShapeGen;


    final LinkedBlockingQueue<String> chunksPendingHandling;
    final LinkedBlockingQueue<String> chunksPendingDeterminations;
    final LinkedBlockingQueue<String> chunksPendingCleaning;
    final LinkedBlockingQueue<String> chunksPendingPreGeneration; 
    final ConcurrentSet<String> chunksPendingRegeneration;
    //private final ConcurrentHashMap<String, ManagedOreClusterChunk> chunksPendingManifestation;

    //(chunkId, (oreType, Vec3i))


    final ConcurrentLinkedSet<String> determinedSourceChunks;
    final ConcurrentSet<String> determinedChunks;
    final ConcurrentSet<String> completeChunks;
    final ConcurrentHashMap<String, Integer> expiredChunks;

    final ConcurrentHashMap<String, LevelChunk> forceLoadedChunks;
    final ConcurrentHashMap<String, ManagedOreClusterChunk> loadedOreClusterChunks;
    final Set<String> initializedOreClusterChunks;

    final ConcurrentHashMap<OreClusterId, Set<String>> existingClustersByType;
    final ConcurrentHashMap<OreClusterId, Set<String>> tentativeClustersByType;
    final ConcurrentHashMap<OreClusterId, Set<String>> removedClustersByType;
    final ConcurrentHashMap<String, Map<OreClusterId, BlockPos>> addedClustersByType;
    final ChunkGenerationOrderHandler mainSpiral;
    private OreClusterCalculator oreClusterCalculator;

    //Threads
    private volatile boolean managerRunning = false;
    private volatile boolean initializing = false;
    private final ConcurrentHashMap<String, Long> threadstarts = new ConcurrentHashMap<>();
    private Thread threadLoad;
    private Thread threadWatchManagedOreChunkLifetime;

    // Execution counters
    private final AtomicInteger loadedChunksCount = new AtomicInteger();
    private final AtomicInteger clusterDeterminationCount = new AtomicInteger();
    private final AtomicInteger clusterCleaningCount = new AtomicInteger();
    private final AtomicInteger clusterGeneratingCount = new AtomicInteger();
    private final AtomicInteger chunkEditingCount = new AtomicInteger();

    private ThreadFactory createThreadFactory(String namePrefix, AtomicInteger counter) {
        return r -> {
            Thread t = new Thread(r);
            t.setName(namePrefix + "-" + counter.incrementAndGet());
            return t;
        };
    }

    private final ExecutorService threadPoolLoadedChunks;
    private final ExecutorService threadPoolClusterDetermination; 
    private final ThreadPoolExecutor threadPoolClusterCleaning;
    private final ThreadPoolExecutor threadPoolClusterGenerating;
    private final ThreadPoolExecutor threadPoolChunkEditing;
    //private final ThreadPoolExecutor threadPoolChunkProcessing;

    /** Constructor **/
    public OreClusterManager(Level level, ModRealTimeConfig config)
    {
        super();
        this.level = level;
        this.config = config;
        this.chunkUtil = ManagedChunkUtility.getInstance(level);
        MANAGERS.put(level, this);

        this.existingClustersByType = new ConcurrentHashMap<>();
        this.tentativeClustersByType = new ConcurrentHashMap<>();
        this.addedClustersByType = new ConcurrentHashMap<>();
        this.removedClustersByType = new ConcurrentHashMap<>();

        this.loadedOreClusterChunks = new ConcurrentHashMap<>();
        this.determinedSourceChunks = new ConcurrentLinkedSet<>();
        this.determinedChunks = new ConcurrentSet<>();
        this.completeChunks = new ConcurrentSet<>();
        this.expiredChunks = new ConcurrentHashMap<>();
        this.forceLoadedChunks = new ConcurrentHashMap<>();

        this.chunksPendingHandling = new LinkedBlockingQueue<>();
        this.chunksPendingDeterminations = new LinkedBlockingQueue<>();
        this.chunksPendingCleaning = new LinkedBlockingQueue<>();
        this.chunksPendingPreGeneration = new LinkedBlockingQueue<>();
        this.chunksPendingRegeneration = new ConcurrentSet<>();

        this.initializedOreClusterChunks = new ConcurrentSet<>();
        //this.chunksPendingManifestation = new ConcurrentHashMap<>();

        this.mainSpiral = new ChunkGenerationOrderHandler(null);
        //Thread pool needs to have one thread max, use Synchronous queue and discard policy
        this.threadPoolLoadedChunks = new ThreadPoolExecutor(1, 1,
        1L, TimeUnit.SECONDS, new SynchronousQueue<>(),
         createThreadFactory("ChunkLoader", loadedChunksCount),
         new ThreadPoolExecutor.DiscardPolicy());
        
        this.threadPoolClusterDetermination = new ThreadPoolExecutor(1, 1,
         30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
         createThreadFactory("ClusterDeterminer", clusterDeterminationCount),
         new ThreadPoolExecutor.DiscardPolicy());

        this.threadPoolClusterCleaning = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            createThreadFactory("ClusterCleaner", clusterCleaningCount),
            new ThreadPoolExecutor.DiscardPolicy());

        this.threadPoolClusterGenerating = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            createThreadFactory("ClusterGenerator", clusterGeneratingCount),
            new ThreadPoolExecutor.DiscardPolicy());

        /*
        this.threadPoolChunkProcessing = new ThreadPoolExecutor(1, 1,
            300L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
         */

        this.threadPoolChunkEditing = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            createThreadFactory("ChunkEditor", chunkEditingCount),
            new ThreadPoolExecutor.DiscardPolicy());


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

    public ConcurrentHashMap<OreClusterId, Set<String>> getTentativeClustersByType() {
        return tentativeClustersByType;
    }

    public ConcurrentHashMap<OreClusterId, Set<String>> getExistingClustersByType() {
        return existingClustersByType;
    }


    public static void init(EventRegistrar reg) {
        MANAGERS = new HashMap<>();
        reg.registerOnDataSave(OreClusterManager::save, EventPriority.High);
        reg.registerOnServerTick(TickType.ON_1200_TICKS, OreClusterManager::on1200Ticks);
    }


    /** Behavior **/
    public void init(LevelAccessor level) 
    {
        ManagedOreClusterChunk.CONFIG = this.config;
        if (ModRealTimeConfig.CLUSTER_SEED == null)
            ModRealTimeConfig.CLUSTER_SEED = GENERAL_CONFIG.getWorldSeed();
        long seed = ModRealTimeConfig.CLUSTER_SEED; 
        this.randSeqClusterPositionGen = new Random(seed);
        //this.randSeqClusterBuildGen = new Random(seed);

        this.oreClusterCalculator = new OreClusterCalculator( this );

        config.getOreConfigs().forEach((oreType, oreConfig) -> {
            existingClustersByType.put(oreType, new HashSet<>());
            tentativeClustersByType.put(oreType, new HashSet<>());
            removedClustersByType.put(oreType, new HashSet<>());
        });
        this.threadLoad = new Thread(this::load);
        this.threadLoad.start();

        //Run all my threads once here
        threadPoolLoadedChunks.submit(this::workerThreadLoadedChunk);
        threadPoolClusterDetermination.submit(this::workerThreadDetermineClusters);
        threadPoolClusterCleaning.submit(this::workerThreadCleanClusters);
        threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
        threadPoolChunkEditing.submit(this::workerThreadEditChunk);
    }

    /**
     * Description: Sweeps loaded ore cluster chunks for any that
     * have been loaded for more than 300s and are still in DETERMINED status
     * @param chunk
     * chunkLifetime
     * chunkDelete
     * chunkExpire
     * lifetime
     */
     private static final Long MAX_DETERMINED_CHUNK_LIFETIME_MILLIS = (DEBUG)
        ? 30_000 : 150_000L;
    private static final Long MIN_EXPIRATION_CHECK_TICK_ALIVE_COUNT = (DEBUG)
        ? 120L : 1200L;
     //timeout
     private static final Long SLEEP_TIME_PER_CHUNK_MILLIS = (DEBUG)
        ? 100L : 100L;
    private static final Long MAX_EXPIRATIONS = 100L;
    private void watchLoadedChunkExpiration()
    {
            boolean errorThrown = false;
            while(managerRunning)
            {
                try {
                    if (this.loadedOreClusterChunks.isEmpty())
                        return;

                     //Get current time
                    Long systemTime = System.currentTimeMillis();
                    Long currentTick = GeneralConfig.getInstance().getTotalTickCount();
                    List<ManagedOreClusterChunk> expired_chunks;

                    Set<String> oldChunks = loadedOreClusterChunks.values().stream()
                        .filter(c -> currentTick - c.getTickLoaded() > MIN_EXPIRATION_CHECK_TICK_ALIVE_COUNT)
                        .map(ManagedOreClusterChunk::getId)
                        .collect(Collectors.toSet());

                    //We need to limit because we will be force loading these chunks so they can save
                    expired_chunks = loadedOreClusterChunks.values().stream()
                        .filter(c -> oldChunks.contains(c.getId()))
                        .filter(c -> !c.updateTimeLastLoaded(systemTime))
                        .filter(c -> (systemTime - c.getTimeLastLoaded()) > MAX_DETERMINED_CHUNK_LIFETIME_MILLIS)
                        .limit(MAX_EXPIRATIONS)
                        .collect(Collectors.toList());

                    if (!expired_chunks.stream().filter(c -> c.getId().equals(TEST_ID)).toList().isEmpty()) {
                        int i = 0;
                    }

                    for (ManagedOreClusterChunk chunk : expired_chunks) {
                        String id = chunk.getId();
                        LevelChunk levelChunk = chunkUtil.getChunk(id,false);
                        if( levelChunk != null) levelChunk.setUnsaved(true);
                        sleep(SLEEP_TIME_PER_CHUNK_MILLIS ); //Sleep for chunks to write data out and unload
                    }

                    for (ManagedOreClusterChunk chunk : expired_chunks) {
                        LoggerProject.logDebug("002004", "Chunk " + chunk.getId() + " has expired");
                        this.editManagedChunk(chunk, this::removeManagedChunk);
                    }

                }
                catch (InterruptedException e) {
                    //continue
                }
                catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Uncaught", e);
                }

            }

        int i = 0;
    }

    private void removeManagedChunk(ManagedOreClusterChunk c )
    {
        String chunkId = c.getId();

        if( !ManagedOreClusterChunk.isComplete(c) ) {
            Integer expiryCount = expiredChunks.get(chunkId);
            if( expiryCount == null ) {
                expiredChunks.put(chunkId, 0);
                expiryCount = 0;
            }
            expiredChunks.put(chunkId, expiryCount + 1 );
        }

        loadedOreClusterChunks.remove(chunkId);
        chunksPendingHandling.remove(chunkId);
        chunksPendingDeterminations.remove(chunkId);
        chunksPendingCleaning.remove(chunkId);
        chunksPendingPreGeneration.remove(chunkId);
        //chunksPendingManifestation.remove(chunkId);

    }


    /**
     * Description: Adds a chunk to the loadedOreClusterChunks map if it does not exist, called when a chunk is deserialized or chunkLoaded.
     * Called before handleLoadedChunkId
     * @param managedChunk
     */
    public void addOrUpdatedLoadedChunk(ManagedOreClusterChunk managedChunk)
    {
        String chunkId = managedChunk.getId();
        if(chunkId.equals(TEST_ID)) {
            int i = 0;
        }

        loadedOreClusterChunks.put(chunkId, managedChunk.getEarliest(loadedOreClusterChunks));
        chunksPendingHandling.add(managedChunk.getId());
        //LoggerProject.logInfo("002001", "Chunk " + chunkId + " added to queue size " + chunksPendingHandling.size());
    }

    /**
     * Description: Handles the id of any recently loaded chunk. If the chunkId has not had a ManagedOreClusterChunk created for it, one is created.
     * Otherwise, the chunk is processed according to its status.
     * @param chunkId
     */
    public void onLoadedChunkId(String chunkId)
    {
        this.LOADS++;
        chunksPendingHandling.add(chunkId);
    }

    /**
     * Description: Handles newly unloaded chunks
     * @param chunk
     */
    public void onChunkUnloaded(ChunkAccess chunk)
    {
        String chunkId = ChunkUtil.getId(chunk);
        ManagedOreClusterChunk managedChunk = loadedOreClusterChunks.get(chunkId);
        if( managedChunk != null ) managedChunk.setTimeUnloaded();
        this.UNLOADS++;
    }


    /**
     * Handle newly loaded chunk
     *
     * 1. If this chunkId exists in existingClusters, check regen
     * 2. If the chunkId exists in exploredChunks, ignore
     * 3. If the chunkId does not exist in exploredChunks, queue a batch
     *
     */
    private void handleChunkLoaded(String chunkId)
    {
        if( chunkId.equals(TEST_ID) ) {
            int i = 0;
        }

        ManagedOreClusterChunk chunk = loadedOreClusterChunks.get(chunkId);
        if( chunk == null )
        {
            if( completeChunks.contains(chunkId) )
                return;
            chunk = ManagedOreClusterChunk.getInstance(this.level, chunkId);
            loadedOreClusterChunks.put(chunkId, chunk);

            if( determinedChunks.contains(chunkId) )
                chunk.setStatus(OreClusterStatus.DETERMINED);
            handleChunkLoaded(chunkId);
            return;
        } else if( chunk.hasClusters() ) {
            chunk.getClusterTypes().forEach((oreType, pos) -> {
                if(pos != null)
                    existingClustersByType.get(oreType).add(chunkId);
            });
        }

        if( ManagedOreClusterChunk.isNoStatus(chunk) )
        {
            if(determinedChunks.contains(chunkId)) {
                chunk.setStatus(OreClusterStatus.DETERMINED);
                handleChunkLoaded(chunkId);
                return;
            }
            chunksPendingDeterminations.add(chunkId);
        }
        else if( ManagedOreClusterChunk.isDetermined(chunk) ) {
            if(chunk.hasClusters()) {
                chunk.getClusterTypes().forEach((oreType, pos) -> {
                    tentativeClustersByType.get(oreType).add(chunkId);
                });
            }
            //chunksPendingCleaning.put(chunkId, chunk);
            //this.threadPoolClusterCleaning.submit(this::workerThreadCleanClusters);
        }
        else if( isCleaned(chunk)
            || ManagedOreClusterChunk.isPregenerated(chunk)
            || ManagedOreClusterChunk.isRegenerated(chunk) )
        {
            //LoggerProject.logDebug("002007","Chunk " + chunkId + " has been cleaned");
            if( chunk.hasClusters() )
            {
                if( ManagedOreClusterChunk.isRegenerated(chunk))
                    this.chunksPendingRegeneration.add(chunkId);
                chunksPendingPreGeneration.add(chunkId);
                chunk.setStatus(OreClusterStatus.CLEANED);
                //this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
            }
        }
        else if( this.chunksPendingRegeneration.contains(chunkId) ) {
            this.triggerRegen(chunkId, false);
        }
        else if( ManagedOreClusterChunk.isGenerated(chunk) )
        {
            //LoggerProject.logDebug("002008","Chunk " + chunkId + " has been generated");
            //chunksPendingManifestation.add(chunkId);
        }
        else if( ManagedOreClusterChunk.isComplete(chunk) )
        {
            //LoggerProject.logDebug("002009","Chunk " + chunkId + " is complete");
            completeChunks.add(chunkId);
            this.editManagedChunk(chunk, this::removeManagedChunk);
        }

    }

    //* WORKER THREADS *//

    /**
     * Newly loaded chunks are polled in a queue awaiting batch handling
     * If the chunk has already been processed it is skipped
     */
    private void workerThreadLoadedChunk()
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadLoadedChunk")) {
            return;
        }

        try
        {
            while(managerRunning) {
                String chunkId = chunksPendingHandling.take();
                handleChunkLoaded(chunkId);
            }
        }
        catch (InterruptedException e)
        {
            LoggerProject.logError("002003","OreClusterManager::workerThreadLoadedChunk() thread interrupted: "
                + e.getMessage());
        }


    }

    private static final int MAX_LOADED_CHUNKS = 64_000;
    private void workerThreadDetermineClusters() 
    {
        if (!WORKER_THREAD_ENABLED.get("workerThreadDetermineClusters")) {
            return;
        }
        //threadstarts.put("workerThreadDetermineClusters", System.currentTimeMillis());
        Throwable thrown = null;
        try
        {
            while(managerRunning)
            {
                String chunkId = chunksPendingDeterminations.take();
                handleChunkDetermination(chunkId);
                if (!this.determinedChunks.contains(chunkId)) {
                    chunksPendingDeterminations.add(chunkId);
                }
            }

        } catch (InterruptedException e) {
            //nothing
        }
        catch (Exception e) {
            thrown = e;
            //LoggerProject.logError("002011.1","Error in workerThreadDetermineClusters: " + e.getMessage());
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
    private void workerThreadCleanClusters() {
        if (!WORKER_THREAD_ENABLED.get("workerThreadCleanClusters")) {
            return;
        }
        threadstarts.put("workerThreadCleanClusters", System.currentTimeMillis());
        Throwable thrown = null;

        try {
            while(managerRunning) {
                String chunkId = chunksPendingCleaning.take();
                ManagedOreClusterChunk chunk = loadedOreClusterChunks.get(chunkId);
                
                if (chunk != null && chunk.hasChunk()) {
                    try {
                        long start = System.nanoTime();
                        editManagedChunk(chunk, this::handleChunkCleaning);
                        long end = System.nanoTime();
                        if(DEBUG) {
                            THREAD_TIMES.get("handleChunkCleaning").add((end - start) / 1_000_000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        LoggerProject.logError("002030","Error cleaning chunk: " + chunkId + " message: " + e.getMessage());
                    }
                }
            }
        } catch (InterruptedException e) {
            // Handle interruption
        } catch (Exception e) {
            thrown = e;
        } finally {
            LoggerProject.threadExited("002028",this, thrown);
        }
    }


    /**
     * Description: Polls prepared chunks from chunksPendingGenerationQueue
     */
    private void workerThreadGenerateClusters() {
        if (!WORKER_THREAD_ENABLED.get("workerThreadGenerateClusters")) {
            return;
        }
        threadstarts.put("workerThreadGenerateClusters", System.currentTimeMillis());
        Throwable thrown = null;

        try {
            while(managerRunning) {
                String chunkId = chunksPendingPreGeneration.take();
                ManagedOreClusterChunk chunk = loadedOreClusterChunks.get(chunkId);
                
                if (chunk != null && chunk.hasReadyClusters()) {
                    long start = System.nanoTime();
                    editManagedChunk(chunk, this::handleChunkClusterPreGeneration);
                    long end = System.nanoTime();
                    if(DEBUG) {
                        THREAD_TIMES.get("handleChunkClusterPreGeneration").add((end - start) / 1_000_000);
                    }
                }
            }
        } catch (InterruptedException e) {
            // Handle interruption
        } catch (Exception e) {
            thrown = e;
        } finally {
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

        try
        {
            while(managerRunning)
            {
                //sleep(1000);
                //Sleep if loaded chunks is empty, else iterate over them
                if( loadedOreClusterChunks.isEmpty() ) {
                    sleep(100);
                    continue;
                }

                if( loadedOreClusterChunks.containsKey(TEST_ID)
                && ManagedOreClusterChunk.isPregenerated(loadedOreClusterChunks.get(TEST_ID)) )
                {
                    int i = 0;
                }

                this.handleChunkReadiness();

                List<ManagedOreClusterChunk> readyChunks = loadedOreClusterChunks.values().stream()
                    .filter(c -> c.isReady())
                    //.filter(c -> c.hasChunk())
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
                    if(DEBUG)
                        THREAD_TIMES.get("handleChunkManifestation").add((end - start) / 1_000_000);

                }

                if( readyChunks.size() < 256 )
                    sleep(100);
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

            List<ManagedOreClusterChunk> availableChunks = loadedOreClusterChunks.values().stream()
                .filter(c -> c.hasChunk() )
                .filter(c -> !c.isReady())
                .toList();

            //Cleaned chunks that have not been harvested yet
            List<ManagedOreClusterChunk> cleanedChunks = availableChunks.stream()
                .filter(c -> isCleaned(c) )
                .filter(c -> !c.hasClusters())              //If it still needs to generate clusters, skip
                .filter(c -> !c.checkClusterHarvested())    //Checks if cluster has been interacted with by player
                .toList();

             //Pre-generated chunks that have not been harvested yet
            List<ManagedOreClusterChunk> preGeneratedChunks = availableChunks.stream()
                .filter(c -> isPregenerated(c) || isRegenerated(c) )
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

            readyChunks.stream().forEach(c -> c.setReady(true));
        }
        catch (Exception e) {
            thrown = e;
            LoggerProject.logError("002031.1","Error in handleChunkReadiness: " + e.getMessage());
        }

    }


    /**
     * Batch process that determines the location of clusters in the next n chunks
     * @param chunkId
     *
     * handleClusterDetermination
     * handleChunkDetermination
     * handleDetermineChunks
     */
    private void handleChunkDetermination(String chunkId)
    {
        // 1. Get and validate chunk is loaded
        if( !chunkUtil.isChunkFullyLoaded(chunkId) ) return;

        LevelChunk chunk = chunkUtil.getChunk(chunkId, false);
        if (chunk == null ) return;

        // 2. Get biomes in chunk
        ManagedOreClusterChunk managedChunk = this.loadedOreClusterChunks.get(chunkId);
        if( managedChunk == null ) return;
        if( managedChunk.getBiomes().isEmpty() ) return;
        Set<OreClusterId> validConfigs = new HashSet<>();
        for( Biome b : managedChunk.getBiomes() ) {
            validConfigs.addAll(config.getAllOreConfigIdsByBiome(level,b));
        }

        // 3. Use spawn rates to determine final clusters
        List<OreClusterId> finalClusters = new ArrayList<>();
        long loc = HBUtil.ChunkUtil.getChunkPos1DMap(chunkId);
        Random rand = new Random((loc + 31) * (ModRealTimeConfig.CLUSTER_SEED+31));
        validConfigs.forEach( id -> {
            OreClusterConfigModel oreConfig = config.getOreConfig(id);
            if( oreConfig == null ) return;
            double rate = (double) oreConfig.oreClusterSpawnRate / ModRealTimeConfig.CHUNK_NORMALIZATION_TOTAL;
            if( rand.nextDouble() < rate ) finalClusters.add(id);
        });

        // 5. Add clusters to managed chunk
        managedChunk.setStatus(OreClusterStatus.DETERMINED);
        this.determinedChunks.add(chunkId);
        this.chunksPendingCleaning.add(chunkId);
        if(!finalClusters.isEmpty())
            managedChunk.addClusterTypes(finalClusters);

        //if(DEBUG) LoggerProject.logDebug("002008", "Queued " + chunkId + " for cluster determination");
    }

    @Deprecated
    private void handleChunkDetermination(int batchSize, String chunkId) 
    {
        LoggerProject.logDebug("002008", "Queued " + chunkId + " for cluster determination");
        determinedSourceChunks.add(chunkId);
        LinkedHashSet<String> chunkIds = getBatchedChunkList(batchSize, chunkId);
        //long step1Time = System.nanoTime();

        //Map<ChunkId, Clusters>
        Map<String, List<OreClusterId>> clusters;
        clusters = oreClusterCalculator.calculateClusterLocations(chunkIds.stream().toList() , randSeqClusterPositionGen);
        //long step2Time = System.nanoTime();

        // #3. Add clusters to determinedClusters
        for( String id: chunkIds)
        {
            if( this.determinedChunks.contains(id) ) continue;

            if( id.equals(TEST_ID)) {
                int i = 0;
            }
        //Create clusters for chunks that aren't loaded yet
            ManagedOreClusterChunk chunk = loadedOreClusterChunks.getOrDefault(id, ManagedOreClusterChunk.getInstance(level, id) );
            this.loadedOreClusterChunks.put(id, chunk);

            if( clusters.get(id) != null )
                chunk.addClusterTypes(clusters.get(id));
            //chunk.setStatus(OreClusterStatus.DETERMINED);
            this.chunksPendingCleaning.put(id, chunk);
            this.determinedChunks.add(id);
        }
        LoggerProject.logDebug("002010","Added " + clusters.size() + " clusters to determinedChunks");


        // #4. Add clusters to tenativeClustersByType
        for( Map.Entry<String, List<OreClusterId>> clusterChunk : clusters.entrySet())
        {
            if( clusterChunk.getValue() == null ) continue;
            for( OreClusterId clusterOreType : clusterChunk.getValue() )
            {
                tentativeClustersByType.get(clusterOreType).add(clusterChunk.getKey());
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
     * cleanClusters
     * cleanChunks
     * @param chunk
     */
     private static int totalCleaned = 0;
     private static int missingOriginalsCleaned = 0;
    private void handleChunkCleaning(ManagedOreClusterChunk chunk)
    {
        if( chunk == null|| chunk.getChunk() == null )
            return;

        try {

            //0. Add a gold_block to blockStateUpdates
            if(DEBUG)
            {
                LevelChunk c = chunk.getChunk(false);
                if( c == null ) return;
                BlockPos pos = c.getPos().getWorldPosition();
                final BlockPos constPos = pos.offset(0, 128, 0);
                chunk.addBlockStateUpdate(Blocks.GOLD_BLOCK.defaultBlockState(), new BlockPos(pos.getX(), 128, pos.getZ()));
                Map<OreClusterId, BlockPos> clusterTypes = chunk.getClusterTypes();
                /*clusterTypes.replaceAll((oreType, sourcePos) -> {
                    return new BlockPos(constPos.getX(), 128, constPos.getZ());
                });*/
            }

            final Map<OreClusterId, OreClusterConfigModel> ORE_CONFIGS = config.getOreConfigs();

            final Set<OreClusterId> COUNTABLE_ORES = ORE_CONFIGS.keySet().stream()
                .filter(id -> config.doesLevelMatch(id, this.level) )
                .filter(id -> config.clustersDoSpawn(id) )
                .collect(Collectors.toSet());

            //No ores to clean, no clusters to build
            if( COUNTABLE_ORES.isEmpty() && !chunk.hasClusters() ) {
               //nothing
            }
            else
            {
                //1. Scan chunk for all cleanable ores, testing each block
                final Set<OreClusterId> clusterIds = chunk.getClusterTypes().keySet();
                final Set<BlockState> COUNTABLE_BLOCKSTATES = chunk.getClusterTypes().keySet()
                    .stream().map(id -> id.getBlock().defaultBlockState() ).collect(Collectors.toSet());
                if( clusterIds.stream().anyMatch(o -> !chunk.hasOreClusterSourcePos(o)) )
                {
                    //Ensure chunk is here
                    if( chunk.getChunk() == null ) return;

                    boolean isSuccessful = oreClusterCalculator.cleanChunkFindAllOres(chunk, COUNTABLE_BLOCKSTATES);
                    if( !isSuccessful ) return;

                    List<OreClusterId> noOresFoundOnClean = chunk.getClusterTypes().keySet().stream()
                        .filter(o -> !chunk.hasOreClusterSourcePos(o))
                        .toList();

                    for(OreClusterId b : noOresFoundOnClean)
                    {
                        chunk.getClusterTypes().remove(b);
                        tentativeClustersByType.get(b).remove(chunk.getId());
                        removedClustersByType.get(b).add(chunk.getId());
                    }
                    missingOriginalsCleaned++;

                }
                totalCleaned++;
                //2. Determine the cluster position for each ore in the managed chunk
                if( chunk.hasClusters() )
                {
                    oreClusterCalculator.cleanChunkSelectClusterPosition(chunk);
                    this.chunksPendingPreGeneration.put(chunk.getId(), chunk);
                    //this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
                }

                //3. Cleans chunk of all ores discovered in the findAllOres method and stored in chunk.getOriginalOres()
                //oreClusterCalculator.cleanChunkOres(chunk, CLEANABLE_ORES);
            }

            //4. Set the chunk status to CLEANED
            chunk.setStatus(OreClusterStatus.CLEANED);
            // No need to remove from blocking queue

            //5. Set the originalOres array to null to free up memory
            chunk.clearOriginalOres();


            //LoggerProject.logInfo("002027", "Cleaning chunk: " + chunk.getId() + " complete. " +
                //"Total cleaned: " + totalCleaned + " Missing originals cleaned: " + missingOriginalsCleaned);

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
     * handleChunkPreGeneration
     * hanldeClusterPregeneration
     * clusterPregeneration
     * chunkPregeneration
     * chunkGeneration
     * generation
     * generateClusters(
     */
    private void handleChunkClusterPreGeneration(ManagedOreClusterChunk chunk)
    {
        
        if( chunk == null || chunk.getChunk(false) == null )
            return;

        if(chunk.getClusterTypes() == null || chunk.getClusterTypes().size() == 0)
            return;

        if( chunk.getId().equals(TEST_ID) ) {
             int i = 0;
        }

        //LoggerProject.logDebug("002015","Generating clusters for chunk: " + chunk.getId());
        boolean onlyRegenerateOres = this.chunksPendingRegeneration.contains(chunk.getId());
        String SKIPPED = null;
        for( OreClusterId oreType : chunk.getClusterTypes().keySet() )
        {
            //1. If we are regenerating, skip any clusters in this chunk that dont' regenerate
            if( onlyRegenerateOres ) {
                OreClusterConfigModel config = this.config.getOreConfigModel(oreType);
                if(!config.oreClusterDoesRegenerate) continue;
            }

            //2. If we don't have a source pos, skip and revert cluster to clean
            BlockPos sourcePos = chunk.getClusterTypes().get(oreType);
            if( sourcePos == null ) {
                LoggerProject.logDebug("002032","No source position for oreType: " + oreType);
                SKIPPED = BlockUtil.blockToString(oreType.getBlock());
                chunk.setStatus(OreClusterStatus.DETERMINED);
                this.chunksPendingCleaning.put(chunk.getId(), chunk);
                continue;
            }

            List<Pair<BlockState, BlockPos>> clusterPos = oreClusterCalculator.generateCluster( chunk, oreType, sourcePos);
            if( clusterPos == null || clusterPos.size() == 0 ) {
                SKIPPED = BlockUtil.blockToString(oreType.getBlock());
                continue;
            }

            //Offset for DEBUG
            Vec3i sourceOffset = new Vec3i(0, 0, 0);
            //if(DEBUG) sourceOffset = new Vec3i(0, 120 - sourcePos.getY(), 0);
            Map<BlockPos, Integer> existingBlockStateUpdates = chunk.getMapBlockStateUpdates();
            for( Pair<BlockState, BlockPos> pos : clusterPos )
            {
                int index = -1;
                if( existingBlockStateUpdates.containsKey(pos.getRight()) )
                    index = existingBlockStateUpdates.get(pos.getRight());
                chunk.addBlockStateUpdate(pos.getLeft(), pos.getRight().offset(sourceOffset), index);
            }
            //add to existingClustersByType
            existingClustersByType.get(oreType).add(chunk.getId());
        }

        if( SKIPPED == null )
        {
            if( onlyRegenerateOres )
                chunk.setStatus(OreClusterStatus.REGENERATED);
            else
                chunk.setStatus(OreClusterStatus.PREGENERATED);
        }

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
     private static Map<String, Integer> manifestUpdates = new ConcurrentHashMap<>();
    private void handleChunkManifestation(ManagedOreClusterChunk chunk)
    {
        //LoggerProject.logDebug("002033","Editing chunk: " + chunk.getId());
        manifestUpdates.putIfAbsent(chunk.getId(), 0);
        if( manifestUpdates.get(chunk.getId()) > 50 ) {
            int i = 0;
        }

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
            List<Pair<BlockState, BlockPos>> updates = chunk.getBlockStateUpdates(64);
            Map<BlockPos, Integer> map = chunk.getMapBlockStateUpdates();
            isSuccessful = ManagedChunk.updateChunkBlockStates(level, updates);
            if( isSuccessful ) {
                manifestUpdates.put(chunk.getId(), manifestUpdates.get(chunk.getId()) + 1);
                List<Integer> removables = updates.stream().map(p -> map.get(p.getRight())).toList();
                chunk.removeBlockStateUpdates(removables);
                if( chunk.countUpdatesRemaining() > 0 )
                    isSuccessful = false;
            }
        }

        if( isSuccessful )
        {
            chunk.setReady(false);
            chunk.clearBlockStateUpdates();

            if( chunk.hasClusters() ) {
                chunk.setStatus(OreClusterStatus.GENERATED);
                chunksPendingRegeneration.remove(chunk.getId());
            }
            else {
                chunk.setStatus(OreClusterStatus.COMPLETE);
                completeChunks.add(chunk.getId());
                this.removeManagedChunk(chunk);
            }
        }
    }

    private void initSerializedChunks(List<String> chunkIds)
    {
        Long start = System.nanoTime();
        for( String id : chunkIds)
        {
            ChunkPos pos = HBUtil.ChunkUtil.getChunkPos(id);
            HBUtil.ChunkUtil.getLevelChunk(level, pos.x, pos.z, false);
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
        if(DEBUG)
            THREAD_TIMES.get("handleChunkInitialization").add((end - start) / 1_000_000); // Convert to milliseconds

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
        Map<String, List<OreClusterId>> clusters;
        clusters = oreClusterCalculator.calculateClusterLocations(chunkIds.stream().toList() , randSeqClusterPositionGen);

        // #3. Add clusters to determinedClusters
        for( String id: chunkIds) {
            this.determinedChunks.add(id);
        }

        // #4. Add clusters to tentativeClustersByType
        for( String id : clusters.keySet() ) {
            List<OreClusterId> clusterTypes = clusters.get(id);
            for( OreClusterId ore : clusterTypes ) {
                if(removedClustersByType.get(ore).contains(id)) continue;
                tentativeClustersByType.get(ore).add(id);
            }
        }
    }

    //* API Methods

    /**
     * Adds a new cluster of a specified type to the requisite ManagedOreChunk
     * @param clusterType
     * @param chunkId
     * @param pos
     * @return true if the operation suceeded
     * addCluster
     */
    public boolean addNewCluster(OreClusterId clusterType, String chunkId, BlockPos pos)
    {
        if(clusterType == null) return false;
        if(this.config.getOreConfigModel(clusterType) == null) return false;

        ManagedOreClusterChunk chunk = this.loadedOreClusterChunks.get(chunkId);
        if( chunk == null )
        {
            this.expiredChunks.remove(chunkId);
            this.loadedOreClusterChunks.put(chunkId, ManagedOreClusterChunk.getInstance(level, chunkId));
        } else {
            if(!isFinished(chunk)) return false;
        }
        chunk = this.loadedOreClusterChunks.get(chunkId);

        if( this.addedClustersByType.get(chunkId) == null )
            this.addedClustersByType.put(chunkId, new HashMap<>());
        this.addedClustersByType.get(chunkId).put(clusterType, pos);
        chunk.addClusterTypes(addedClustersByType.get(chunkId));

        this.completeChunks.remove(chunkId);
        return forceProcessChunk(chunkId, OreClusterStatus.CLEANED);
    }

    /**
     * Attempts to load a ManagedOreClusterChunk into memory by forceloading the chunk, returns
     * false if there is no instance in managedOreClusterChunks at the time this method returns;
     * @param chunkId
     * @return true if the chunk is in loadedOreClusterChunks, false otherwise
     */
    public boolean forceReloadChunk(String chunkId)
    {
        ManagedOreClusterChunk chunk = this.loadedOreClusterChunks.get(chunkId);
        if( chunk != null ) return true;
            final int MAX_TRIES = 10; int count = 0;
            while( chunk == null && count < MAX_TRIES ) {
                ManagedChunk parent = chunkUtil.getManagedChunk(chunkId);
                if( parent == null ) return false;
                LevelChunk l =  parent.getCachedLevelChunk();
                if( l != null ) this.forceLoadedChunks.put(chunkId, l);
                chunk = this.loadedOreClusterChunks.get(chunkId);
                count++;
            }
            if(this.forceLoadedChunks.get(chunkId) == null) return false;
        return chunk != null;
    }

    public boolean forceProcessChunk(String chunkId) {
        return this.forceProcessChunk(chunkId, OreClusterStatus.NONE);
    }

    /**
     *
     * @param chunkId
     * @param fromStatus skips past this status when loading the chunk
     * @return boolean indicating the reproccessing was successful
     *
     * forceReload
     */
    public boolean forceProcessChunk(String chunkId, OreClusterStatus fromStatus)
    {
        if(this.completeChunks.contains(chunkId)) return true;

        try {

            ManagedOreClusterChunk chunk = this.loadedOreClusterChunks.get(chunkId);
            if(chunk == null) {
                if( !forceReloadChunk(chunkId) ) return false;
                chunk = this.loadedOreClusterChunks.getOrDefault(chunkId, ManagedOreClusterChunk.getInstance(level, chunkId));
                fromStatus = OreClusterStatus.NONE;
                chunk.setStatus(OreClusterStatus.NONE);
            } else {
                chunk.setStatus(fromStatus);
            }

            this.removeManagedChunk(chunk);
            this.loadedOreClusterChunks.put(chunkId, chunk);

            final ManagedOreClusterChunk CHUNK_REF = chunk;
            final OreClusterStatus minStatus = fromStatus;
            Function<OreClusterStatus, Boolean> hasStatus = (s) -> {
                return s.ordinal() <= CHUNK_REF.getStatus().ordinal() || minStatus.ordinal() >= s.ordinal();
            };

            if( !hasStatus.apply(OreClusterStatus.DETERMINED) )
            {
                //add cluster from existing clusters by type
                Map<OreClusterId, BlockPos> clusters = new HashMap<>(32);
                tentativeClustersByType.entrySet().stream()
                    .filter(e -> e.getValue().contains(chunkId))
                    .forEach(e -> clusters.put(e.getKey(), null));

                chunk.addClusterTypes(clusters);
                if( isNoStatus(chunk) ) chunk.setStatus( OreClusterStatus.DETERMINED );
            }

            final int MAX_TRIES = 10;   //10 tries to load chunk
            int count = 0;              //here
            while( !hasStatus.apply(OreClusterStatus.CLEANED) ) {
                editManagedChunk(chunk, this::handleChunkCleaning);
                if( count++ > MAX_TRIES ) return false;
            }

            count = 0;
            if( chunk.hasClusters() )
            {
                while( !hasStatus.apply(OreClusterStatus.PREGENERATED) ) {
                    editManagedChunk(chunk, this::handleChunkClusterPreGeneration);
                    if (count++ > MAX_TRIES) return false;
                }
                this.chunksPendingRegeneration.remove(chunkId);

            }

            editManagedChunk(chunk, (c) -> c.setReady(true));
            int i = 0;

        } catch (Exception e) {
            LoggerProject.logError("002017","Error in force loading chunk: " + chunkId +"\n" + e.getMessage());
            e.printStackTrace();
            return false;
        }
        finally {
            forceLoadedChunks.remove(chunkId);
        }
        return true;

    }

    void triggerRegen()
    {
        //0. Clear all health check data
        this.clearHealthCheckData();

        //1. Get list of all chunkIds with clusters
        LinkedHashSet<String> regenableChunks = new LinkedHashSet<>();
        for( OreClusterId clusterType : existingClustersByType.keySet() )
        {
            OreClusterConfigModel model = this.config.getOreConfigModel(clusterType);
            if( model == null || !model.oreClusterDoesRegenerate ) continue;
            regenableChunks.addAll(existingClustersByType.get(clusterType));
        }

        regenableChunks.forEach(c -> this.triggerRegen(c, false));
        //this.threadPoolClusterGenerating.submit(this::workerThreadGenerateClusters);
    }

    boolean triggerRegen(String chunkId, boolean force)
    {
        AtomicBoolean hasCluster = new AtomicBoolean(false);
        existingClustersByType.values().forEach(list -> {
            if( list.contains(chunkId) ) hasCluster.set(true);
        });

        if( !hasCluster.get() ) {
            LoggerProject.logWarning("002015","Chunk " + chunkId + " does not have any clusters to regenerate. Rejected.");
            return false;
        }

        chunksPendingRegeneration.add(chunkId);
        ManagedOreClusterChunk chunk = loadedOreClusterChunks.get(chunkId);
        if( chunk == null ) return false;

        final int REGENERATED = OreClusterStatus.REGENERATED.ordinal();
        if(chunk.getStatus().ordinal() < REGENERATED ) {
            chunksPendingRegeneration.remove(chunkId);
            return true;
        }
        //chunk.setStatus(OreClusterStatus.REGENERATED); -- not until chunk is successfully re-pregenerated
        chunksPendingPreGeneration.put(chunkId, chunk);
        if( force ) { this.forceProcessChunk(chunkId, OreClusterStatus.CLEANED); }
        return true;
    }

    /**
     *              UTILITY SECTION
     */

    private void clearHealthCheckData() {
        if (DEBUG && THREAD_TIMES != null) {
            THREAD_TIMES.values().forEach(List::clear);
        }
    }


    /**
     * Batch process that determines the location of clusters in the next n chunks
     * Chunk cluster determinations are made spirally from the 'start' chunk, up, right, down, left
     */
    private LinkedHashSet<String> getBatchedChunkList(int batchSize, String startId)
    {
        LinkedHashSet<String> chunkIds = new LinkedHashSet<>();
        ChunkPos pos = HBUtil.ChunkUtil.getChunkPos(startId);
        ChunkGenerationOrderHandler chunkIdGeneratorHandler = mainSpiral;
        if (mainSpiral.testMainSpiralRangeExceeded()) {
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


        public Level getLevel() {
            return level;
        }

        public ManagedOreClusterChunk getLoadedChunk(String chunkId) {
            return loadedOreClusterChunks.get(chunkId);
        }

        public LevelChunk getForceLoadedChunk(String chunkId) {
            return this.forceLoadedChunks.get(chunkId);
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
        this.save(DatastoreSaveEvent.create());

        managerRunning = false;

        threadPoolLoadedChunks.shutdownNow();
        threadPoolClusterDetermination.shutdownNow();
        threadPoolClusterCleaning.shutdownNow();
        threadPoolClusterGenerating.shutdownNow();
        threadPoolChunkEditing.shutdownNow();
        if( this.threadLoad != null )
            this.threadLoad.interrupt();
        if( this.threadWatchManagedOreChunkLifetime != null )
            this.threadWatchManagedOreChunkLifetime.interrupt();

    }

    private void load() {
        this.managerRunning = false;
        this.initializing = true;
        DataStore ds = GeneralConfig.getInstance().getDataStore();
        LevelSaveData levelData = ds.getOrCreateLevelSaveData(Constants.MOD_ID, level);

        if( levelData.get("determinedSourceChunks") == null ) {
            this.initializing = false;
            this.managerRunning = true;
            return;
        }

        //1. Extract "removedClusters" from levelData
        JsonElement removedClusters = levelData.get("removedClusters");
        if( removedClusters == null || removedClusters.isJsonNull() ) {
            //skip
        } else {
            JsonObject json = removedClusters.getAsJsonObject();
            for( String oreType : json.keySet() ) {
                OreClusterId id = this.config.getOreConfigId(Integer.parseInt(oreType));
                if(id == null) continue;
                JsonArray ids = json.get(oreType).getAsJsonArray();
                removedClustersByType.get(id).addAll(ids.asList().stream().map(JsonElement::getAsString).toList());
            }
        }


        //2. get AddedClusters
        JsonElement addedClusters = levelData.get("addedClusters");
        if( addedClusters == null || addedClusters.isJsonNull() ) {
            //skip
        } else {
            JsonObject json = addedClusters.getAsJsonObject();
            for( String oreType : json.keySet() ) {
                OreClusterId oreClusterId = this.config.getOreConfigId(Integer.parseInt(oreType));
                if(oreClusterId == null) continue;
                JsonArray chunkIds = json.get(oreType).getAsJsonArray();
                tentativeClustersByType.get(oreClusterId).addAll(chunkIds.asList().stream().map(JsonElement::getAsString).toList());
                existingClustersByType.get(oreClusterId).addAll(chunkIds.asList().stream().map(JsonElement::getAsString).toList());
                List<String> listIds = chunkIds.asList().stream().map(JsonElement::getAsString).toList();
                for( String id : listIds ) {
                    addedClustersByType.put(id, new HashMap<>());
                    addedClustersByType.get(id).put(oreClusterId, null);
                }
            }
        }

        //3. Get chunksPendingRegeneration
        JsonElement regenChunks = levelData.get("chunksPendingRegen");
        if( regenChunks == null || regenChunks.isJsonNull() ) {
            //skip
        } else {
            JsonArray ids = regenChunks.getAsJsonArray();
            chunksPendingRegeneration.addAll(ids.asList().stream().map(JsonElement::getAsString).toList());
        }

        //4. Extract "determinedSourceChunks" from levelData
        JsonArray ids = levelData.get("determinedSourceChunks").getAsJsonArray();
        List<String> chunkIds = ids.asList().stream().map(JsonElement::getAsString).toList();
        //this.initSerializedChunks(chunkIds);

         //5. Remove all ids from tentativeClustersByType using removedClusters
        for( OreClusterId oreType : removedClustersByType.keySet() ) {
            for( String id : removedClustersByType.get(oreType) ) {
                tentativeClustersByType.get(oreType).remove(id);
            }
        }

        this.managerRunning = true;
    }


    /**
     * Description: Saves the determinedSourceChunks to levelSavedata
      */
    private void save(DataStore ds)
    {
        //Create new Mod Datastore, if one does not exist for this mod,
        //read determinedSourceChunks into an array and save it to levelSavedata
        if (ds == null) return;
        //if(true) return;

        LevelSaveData levelData = ds.getOrCreateLevelSaveData(Constants.MOD_ID, level);


        String[] ids = determinedSourceChunks.toArray(new String[0]);
        //levelData.addProperty("determinedSourceChunks", HBUtil.FileIO.arrayToJson(ids));

        Function<BlockState, String> toName = (bs) -> HBUtil.BlockUtil.blockToString(bs.getBlock());
        Function<Set<String>, JsonElement> toArray = (list) -> HBUtil.FileIO.arrayToJson(list.toArray(new String[0]));

        JsonObject removedClusters = new JsonObject();
        for( OreClusterId clusterId : removedClustersByType.keySet()) {
            removedClusters.add(clusterId.getStringId(), toArray.apply(removedClustersByType.get(clusterId)));
        }
        //levelData.addProperty("removedClusters", removedClusters);

        //save addedClusters
        JsonObject addedClusters = new JsonObject();
        Map<OreClusterId, Set<String>> addedClustersByOreClusterId = new HashMap<>();
        //Fill Map with BlockState from oreConfig mapped to empty sets
        for( OreClusterId ore : config.getOreConfigs().keySet() ) {
            Set<String> clusterIds = addedClustersByType.entrySet().stream()
                .filter(e -> e.getValue().containsKey(ore))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
            addedClustersByOreClusterId.put(ore, clusterIds);
        }
        for( OreClusterId ore : addedClustersByOreClusterId.keySet()) {
            addedClusters.add( ore.getStringId(), toArray.apply(addedClustersByOreClusterId.get(ore)));
        }
        //levelData.addProperty("addedClusters", addedClusters);

        //save chunksPendingRegen
        String[] regenIds = chunksPendingRegeneration.toArray(new String[0]);
        levelData.addProperty("chunksPendingRegen", HBUtil.FileIO.arrayToJson(regenIds));

    }

    //* STATIC METHODS

    public static void onChunkLoad(ChunkLoadingEvent.Load event)
    {
        LevelAccessor level = event.getLevel();
        if( level == null || level.isClientSide() )
            return;

        OreClusterManager manager = OreClustersAndRegenMain.getManagers().get( level );
        if( manager != null ) {
            manager.onLoadedChunkId( HBUtil.ChunkUtil.getId(event.getChunk())  );
        }
    }
    //END onChunkLoad

    public static void addManagedOreClusterChunk(ManagedOreClusterChunk managedChunk)
    {
        OreClusterManager manager = OreClustersAndRegenMain.getManagers().get( managedChunk.getLevel() );
        if( manager != null ) {
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
            OreClusterManager manager = OreClustersAndRegenMain.getManagers().get( level );
            if( manager != null ) {
                manager.onChunkUnloaded(event.getChunk());
            }
        }
    }

    public static ManagedOreClusterChunk getManagedOreClusterChunk(LevelAccessor level, LevelChunk chunk) {
        OreClusterManager manager = OreClustersAndRegenMain.getManagers().get(level);
        if( manager == null ) return null;
        return manager.getManagedOreClusterChunk(chunk);
    }

    public static OreClusterManager getManager(LevelAccessor level) {
        return OreClustersAndRegenMain.getManagers().get(level);
    }

    public Set<String> getDeterminedChunks() {
        return this.determinedChunks;
    }

    public ManagedOreClusterChunk getManagedOreClusterChunk(ChunkAccess chunk) {
        return this.getManagedOreClusterChunk(ChunkUtil.getId(chunk));
    }

    public ManagedOreClusterChunk getManagedOreClusterChunk(String chunkId) {
        this.loadedOreClusterChunks.putIfAbsent(chunkId, ManagedOreClusterChunk.getInstance(level, chunkId));
        return this.loadedOreClusterChunks.get(chunkId);
    }

    public ManagedOreClusterChunk getDeterminedOreClusterChunk(ChunkAccess chunk) {
        return this.getDeterminedOreClusterChunk(ChunkUtil.getId(chunk));
    }

    public ManagedOreClusterChunk getDeterminedOreClusterChunk(String chunkId) {
        return this.chunksPendingCleaning.get(chunkId);
    }


    //* EVENTS

    private static void save(DatastoreSaveEvent event) {
        for( OreClusterManager m : MANAGERS.values() ) {
            m.save(event.getDataStore());
        }
    }

    private static void on1200Ticks(ServerTickEvent event) {
        for (OreClusterManager m : MANAGERS.values()) {
            m.clearHealthCheckData();
            if(m.threadWatchManagedOreChunkLifetime == null || !m.threadWatchManagedOreChunkLifetime.isAlive()) {
                m.threadWatchManagedOreChunkLifetime = new Thread(m::watchLoadedChunkExpiration);
                m.threadWatchManagedOreChunkLifetime.start();
            }
        }
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
        private int total;
        private int count;
        private int dirCount;
        private int[] dir;

        public ChunkGenerationOrderHandler(ChunkPos start)
        {
            this.currentPos = (start == null) ? new ChunkPos(0, 0) : start;
            this.total = 0;
            this.count = 1;
            this.dirCount = 0;
            this.dir = UP;
        }

        public ChunkPos getNextSpiralChunk()
        {
            if(total == 0) {
                total++;
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
            total++;
            dirCount++;

            return currentPos;
        }

        private int[] getNextDirection() {
            int index = Arrays.asList(DIRECTIONS).indexOf(dir);
            return DIRECTIONS[(index + 1) % DIRECTIONS.length];
        }

        public boolean testMainSpiralRangeExceeded() {
            return total >= Math.pow( ModRealTimeConfig.ORE_CLUSTER_DTRM_RADIUS_STRATEGY_CHANGE, 2);
        }
    }
}
