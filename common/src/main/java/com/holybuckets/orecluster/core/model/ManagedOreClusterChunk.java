package com.holybuckets.orecluster.core.model;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtilityAccessor;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.orecluster.core.OreClusterManager;
import com.holybuckets.orecluster.core.OreClusterStatus;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import com.holybuckets.foundation.HBUtil.*;


/**
 * Class: ManagedChunk
 * Description: Dedicating a class to hold the many manged states of a chunk preparing for cluster generation
 *
 *  #Variables
 *  - LevelChunk chunk: The chunk object
 *  - ChunkPos pos: The 2D position of the chunk in the world
 *  - String id: The unique id of the chunk
 *  - String status: The status of the chunk in the cluster generation process
 *
 *  - HashMap<String, Vec3i> clusters: The clusters in the chunk
 *  - isLoaded: The chunk is loaded
 *
 *  #Methods
 *  - Getters and Setters
 *  - save: Save the data as NBT using compoundTag
 *
 */

public class ManagedOreClusterChunk implements IMangedChunkData {

    private static final String CLASS_ID = "003";
    private static final String NBT_KEY_HEADER = "managedOreClusterChunk";
    
    public static final String TEST_ID = "-15,-15";


    public static final int MAX_ORIGINAL_ORES = 8;

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedOreClusterChunk.class, () -> new ManagedOreClusterChunk(null) );
    }

    /** Variables **/
    private LevelAccessor level;
    private String id;
    private ChunkPos pos;
    private OreClusterStatus status;
    private long timeUnloaded;
    private long timeLastLoaded;
    private long tickLoaded;
    private boolean isReady;

    private HashMap<BlockState, BlockPos> clusterTypes;
    private Map<BlockState, Pair<BlockPos, MutableInt>> originalOres;
    private Map<Integer, Pair<BlockState, BlockPos>> blockStateUpdates;

    private Random managedRandom;
    private ReentrantLock lock = new ReentrantLock();

    //private List<Pair<String, Vec3i>> clusters;

    /** Constructors **/

    //Default constructor - creates dummy node to be loaded from HashMap later
    private ManagedOreClusterChunk(LevelAccessor level)
    {
        super();
        this.level = level;
        this.id = null;
        this.pos = null;
        this.status = OreClusterStatus.NONE;
        this.timeUnloaded = -1;
        this.timeLastLoaded = System.currentTimeMillis();
        this.tickLoaded = GeneralConfig.getInstance().getTotalTickCount();
        this.isReady = false;
        this.clusterTypes = null;
        this.blockStateUpdates = new ConcurrentHashMap<>();
        this.originalOres = new HashMap<>();

    }

    //One for building with id
    private ManagedOreClusterChunk(LevelAccessor level, String id)
     {
        this(level);
        this.setId(id);
        this.pos = ChunkUtil.getPos( id );
    }



    /** Getters **/
    public LevelChunk getChunk(boolean forceLoad)
    {
        ManagedChunk parent = ManagedOreClusterChunk.getParent(level, id);
        if(parent == null)
            return null;
        return parent.getChunk(forceLoad);
    }

    public LevelChunk getChunk() {
        return getChunk(false);
    }

    public boolean testChunkStatusOrAfter(ChunkStatus status)
    {
        ManagedChunk parent = ManagedOreClusterChunk.getParent(level, id);
        if(parent == null) return false;

        ChunkAccess chunk = parent.getChunk(false);
        if(chunk == null) return false;
        LevelChunk c;

        try { return chunk.getStatus().isOrAfter(status); }
        catch(Exception e) { return false; }
    }

    public boolean testChunkLoadedAndEditable() {
        return ManagedChunkUtilityAccessor.isChunkFullyLoaded(this.level, this.id);
    }

    public boolean hasChunk() {
        return getChunk(false) != null;
    }

    public ChunkPos getPos() {
        return pos;
    }

    public String getId() {
        return id;
    }

    public OreClusterStatus getStatus() {
        return status;
    }

    public HashMap<BlockState, BlockPos> getClusterTypes() {
    if(this.clusterTypes == null)
        return new HashMap<>();
        return clusterTypes;
    }

    public boolean hasClusters() {
        if(this.clusterTypes == null)
            return false;
        return this.clusterTypes.size() > 0;
    }

    public boolean hasReadyClusters() {
        if(!this.hasClusters())
            return false;

        //check if all clusters have positions
        boolean ready = this.clusterTypes.values().stream()
            .allMatch( (pos) -> pos != null );

        return ready;
    }

    public List<Pair<BlockState, BlockPos>> getBlockStateUpdates() {
        return new ArrayList<>(blockStateUpdates.values());
    }

    /**
     * Maps the current block state update at a given BlockPos to its index in the blockStateUpdates list
     * @return
     */
    public Map<BlockPos, Integer> getMapBlockStateUpdates() {
        Map<BlockPos, Integer> map = new HashMap<>();
        int i = 0;
        for(Pair<BlockState, BlockPos> pair : blockStateUpdates.values())
        {
            map.put(pair.getRight(), i);
            i++;
        }
        return map;
    }

    public Map<BlockState, Pair<BlockPos, MutableInt>> getOriginalOres() {
        return originalOres;
    }

    public LevelAccessor getLevel() { return level; }

    public Long getTimeUnloaded() { return timeUnloaded; }

    public Long getTimeLastLoaded() { return timeLastLoaded; }

    public Long getTickLoaded() { return tickLoaded; }

    public boolean isReady() { return isReady; }

    public Random getChunkRandom() {
        return ManagedChunkUtilityAccessor.getChunkRandom(this.pos, ModRealTimeConfig.CLUSTER_SEED );
    }

    public synchronized ReentrantLock getLock() {
        return lock;
    }

    /** Setters **/

    @Override
    public void setId(String id) {
        if(id == null) return;
        this.id = id;
        this.pos = ChunkUtil.getPos(id);
        this.managedRandom = ManagedChunkUtilityAccessor.getChunkRandom(this.pos);
    }

    @Override
    public void setLevel(LevelAccessor level) { this.level = level; }

    private static final OreClusterStatus current = OreClusterStatus.CLEANED;
    private static final OreClusterStatus delinquent = OreClusterStatus.DETERMINED;
    public void setStatus(OreClusterStatus status) {
        if(this.status == current && status == delinquent) {
            LoggerProject.logInfo("003012", "Chunk " + this.id + " attempted to set delinquent status" + status);
        }
    this.status = status;
    }

    /**
     * Applies resevoir sampling to determine which single ore should server as cluster position
     * for the cluster type. Does not consider ore Height when sampling
     * @param state
     * @return
     */
    public boolean sampleAddOre(BlockState state)
    {
        if(this.originalOres == null)
            return false;

        if(!this.originalOres.containsKey(state)) {
            this.originalOres.put(state, Pair.of(null, new MutableInt(1)));
            return true;
        }

        MutableInt count = this.originalOres.get(state).getRight();
        if (this.managedRandom.nextFloat() <= (1.0f / count.getAndAdd(1) )) {
            return true;
        }

        return false;
    }

    public void addOre(BlockState state, BlockPos pos, boolean force)
    {
        //1. Get the pair
        Pair<BlockPos, MutableInt> pair = this.originalOres.get(state);

        if(pair == null) {
            pair = Pair.of(pos, new MutableInt(1));
            this.originalOres.put(state, pair);
            return;
        }

        //Count was incremented by the previous call to sampleAddOre or this one
        if(force || sampleAddOre(state)) {
            pair = Pair.of(pos, pair.getRight());
            this.originalOres.put(state, pair);
        }

    }

    public boolean hasOreClusterSourcePos(BlockState b) {
        if(this.originalOres == null) return false;
        if(!this.originalOres.containsKey(b)) return false;
        return this.originalOres.get(b).getLeft() != null;
    }

    public BlockPos getOreClusterSourcePos(BlockState b) {
        if(this.originalOres == null) return null;
        if(!this.originalOres.containsKey(b)) return null;
        return this.originalOres.get(b).getLeft();
    }

    public void clearOriginalOres() {
        if(isNoStatus(this) || isDetermined(this) ) return;
        if(this.originalOres == null) return;
        this.originalOres.clear();
        this.originalOres = null;
    }


    public void setReady(boolean ready) {
        this.isReady = ready;
    }

    /**
     * Updates time loaded with the current system time in milliseconds
     */
    public void setTimeUnloaded() {
        this.timeUnloaded = System.currentTimeMillis();
    }

    /**
     * Updates time with specified time if ManagedChunkUtilityAccessor.isLoaded returns true
     * @param currentTime
     */
    public boolean updateTimeLastLoaded(Long currentTime) {
        if(ManagedChunkUtilityAccessor.isLoaded(this.level, this.id)) {
            this.timeLastLoaded = currentTime;
            return true;
        }
        return false;
    }

    /** Other Methods **/
    public void addClusterTypes(List<BlockState> clusters)
    {
        if( clusters == null )
            return;
        Map<BlockState, BlockPos> clusterMap = new HashMap<>();
        for(BlockState state : clusters) {
            clusterMap.put(state, null);
        }
        this.addClusterTypes(clusterMap);
    }

    public void addClusterTypes(Map<BlockState, BlockPos> clusterMap)
    {
        if( clusterMap == null )
            return;

        if( clusterMap.size() == 0 )
            return;

        if( this.clusterTypes == null )
            this.clusterTypes = new HashMap<>();

        this.clusterTypes.putAll( clusterMap );
        //LoggerProject.logDebug("003010", "Adding clusterTypes: " + this.clusterTypes);
    }

    public void addBlockStateUpdate(BlockState block, BlockPos pos, int index) {
        this.addBlockStateUpdate( Pair.of(block, pos), index);
    }


    public void addBlockStateUpdate(BlockState block, BlockPos pos) {
        this.addBlockStateUpdate( Pair.of(block, pos) );
    }

    public void addBlockStateUpdate(Pair<BlockState, BlockPos> pair) {
        this.addBlockStateUpdate(pair, -1);
    }

    public void addBlockStateUpdate(Pair<BlockState, BlockPos> pair, int index) {
        if(index < 0) index = blockStateUpdates.size();
        blockStateUpdates.put(index, pair);
    }



    /**
     * Check if any updatable blocks in the chunk have been changed
     * @return true if the Chunk has been harvested. False if the chunk is not loaded or the chunk has not been cleaned.
     */
    public boolean checkClusterHarvested()
    {
        if( this.id.equals(TEST_ID)) {
            int i = 0;
        }

        LevelChunk chunk = getChunk(false);
        if(chunk == null)
            return false;

        if(this.status == OreClusterStatus.HARVESTED)
            return true;

        if( this.status != OreClusterStatus.GENERATED )
            return false;


        //If any block in the chunk does not equal a block in block state updates, set the chunk as harvested
        for(Pair<BlockState, BlockPos> pair : this.blockStateUpdates.values())
        {
            BlockState block = pair.getLeft();
            BlockPos pos = pair.getRight();
            if(!chunk.getBlockState(pos).getBlock().equals( block ))
            {
                LoggerProject.logDebug("003011", "Cluster Harvested: " + BlockUtil.positionToString(pos));
                this.status = OreClusterStatus.HARVESTED;
                blockStateUpdates.clear();
                return true;
            }
        }

        return false;
    }

    public boolean hasBlockUpdates() {
        return this.blockStateUpdates != null && this.blockStateUpdates.size() > 0;
    }

    public void clearBlockStateUpdates() {
        this.blockStateUpdates.clear(); 
    }

    public ManagedOreClusterChunk getEarliest(Map<String, ManagedOreClusterChunk> loadedChunks) {
        ManagedOreClusterChunk existing = loadedChunks.get(this.id);
        if(existing == null)
            return this;

        if(existing.getTickLoaded() < this.getTickLoaded())
            return existing;
        else
            return this;
    }


    public BlockState mapBlockState(BlockState state)
    {
        Map<BlockState, OreClusterConfigModel> ORE_CONFIGS = OreClusterManager.getManager(level).getConfig().getOreConfigs();

        Block[] replacements = ORE_CONFIGS.get(state).oreClusterReplaceableEmptyBlocks.toArray(new Block[0]);
        Float modifier = ORE_CONFIGS.get(state).oreVeinModifier;

        //If we want mod ~ 0.8 (80% of ore to spawn) then 20% of the time we will replace the block
        if (managedRandom.nextFloat() > modifier) {
            return replacements[managedRandom.nextInt(replacements.length)].defaultBlockState();
        }

        return state;
    }

    /** OVERRIDES **/

    @Override
    public ManagedOreClusterChunk getStaticInstance(LevelAccessor level, String id)
    {
        if(id == null || level == null )
         return null;

        OreClusterManager manager = OreClustersAndRegenMain.getManagers().get(level);
        if(manager != null)
        {
            if(manager.getLoadedChunk(id) != null)
                return manager.getLoadedChunk(id);
        }

        ManagedOreClusterChunk chunk = ManagedOreClusterChunk.getInstance(level, id);
        return chunk;
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(ManagedOreClusterChunk.class.getName()) && this.id != null;
    }

    private static Set<String> loadedIds = new HashSet<>();
    @Override
    public void handleChunkLoaded(ChunkLoadingEvent.Load event)
    {
        loadedIds.add(this.id);
        this.level = event.getLevel();
        this.pos = event.getChunkPos();
        this.timeUnloaded = -1;
        OreClusterManager.onChunkLoad(event);
    }


    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event)
    {
        OreClusterManager.onChunkUnload(event);
    }

    /** STATIC METHODS **/

    /**
     * Get an instance of the ManagedOreClusterChunk using a loaded chunk
     * @param level
     * @param chunk
     * @return
     */
    public static ManagedOreClusterChunk getInstance(LevelAccessor level, LevelChunk chunk) {
        return ManagedOreClusterChunk.getInstance(level, ChunkUtil.getId( chunk ));
    }

    /**
     * Get an instance of the ManagedOreClusterChunk using an existing id, for a chunk that may not be loaded yet
     * @param level
     * @param id
     * @return
     */
    public static ManagedOreClusterChunk getInstance(LevelAccessor level, String id)
    {

        ManagedChunk parent = getParent(level, id);
        if(parent == null)
            return new ManagedOreClusterChunk(level, id);

        ManagedOreClusterChunk c = (ManagedOreClusterChunk) parent.getSubclass(ManagedOreClusterChunk.class);
        if( c == null)
            return new ManagedOreClusterChunk(level, id);

        return c;
    }

    public static ManagedChunk getParent(LevelAccessor level, String id) {
        return ManagedChunkUtilityAccessor.getManagedChunk(level, id);
    }

    public  static boolean isNoStatus(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.NONE;
    }

    public static boolean isDetermined(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.DETERMINED;
    }

    public static boolean isCleaned(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.CLEANED;
    }

    public static boolean isPregenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.PREGENERATED;
    }

    public static boolean isRegenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.REGENERATED;
    }

    public static boolean isGenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.GENERATED;
    }

    public static boolean isHarvested(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.HARVESTED;
    }

    public static boolean isComplete(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == OreClusterStatus.COMPLETE;
    }

    public static boolean isReady(ManagedOreClusterChunk chunk) {
        return chunk.isReady;
    }

    public static boolean isLoaded(ManagedOreClusterChunk chunk) {
        return ManagedChunkUtilityAccessor.isLoaded( chunk.getLevel(), chunk.getId() );
    }


    /** SERIALIZERS **/

    @Override
    public CompoundTag serializeNBT()
    {
        //LoggerProject.logDebug("003002", "Serializing ManagedOreClusterChunk");

        CompoundTag details = new CompoundTag();
        details.putString("id", this.id);
        details.putLong("tickLoaded", this.tickLoaded);

        if( this.id.equals(TEST_ID)) {
            int i = 0;
        }


        if( this.hasBlockUpdates() )
            details.putString("status", OreClusterStatus.DETERMINED.toString());
        else
            details.putString("status", this.status.toString() );


        //Cluster Types
        {
            if(this.clusterTypes == null || this.clusterTypes.size() == 0) {
                details.putString("clusterTypes", "");
            }
            else
            {
                Map<Block, List<BlockPos>> clusters = new HashMap<>();
                this.clusterTypes.keySet().forEach((k) -> clusters.put(k.getBlock(), new ArrayList<>()));
                for(Map.Entry<BlockState, BlockPos> entry : this.clusterTypes.entrySet())
                {
                    Block block = entry.getKey().getBlock();
                    BlockPos pos = entry.getValue();
                    if(pos != null)
                        clusters.get(block).add(pos);
                }
                String clusterTypes = BlockUtil.serializeBlockPairs(clusters);
                details.putString("clusterTypes", clusterTypes);
            }
        }


        //blockStateUpdates - dont serialize over 10KB
        /*
        {
            if(this.blockStateUpdates == null || this.blockStateUpdates.size() == 0) {
                details.putString("blockStateUpdates", "");
            }
            else
            {
                Map<Block, List<BlockPos>> blocks = new HashMap<>();
                this.blockStateUpdates.forEach((pair) -> {
                    Block block = pair.getLeft();
                    if(!blocks.containsKey(block))
                        blocks.put(block, new ArrayList<>());
                });

                for(Pair<Block, BlockPos> pair : this.blockStateUpdates)
                {
                    Block block = pair.getLeft();
                    BlockPos pos = pair.getRight();
                    blocks.get(block).add(pos);
                }

                String blockStateUpdates = BlockUtil.serializeBlockPairs(blocks);
                details.putString("blockStateUpdates", blockStateUpdates);
            }

        }
        */

        LoggerProject.logDebug("003007", "Serializing ManagedOreChunk: " + details);

        return details;
    }

    private static Set<String> deserializedIds = new HashSet<>();
    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        if(tag == null || tag.isEmpty())
            return;

        deserializedIds.add(this.id);
        this.pos = ChunkUtil.getPos( this.id );
        this.tickLoaded = tag.getLong("tickLoaded");
        this.timeUnloaded = -1;
        this.status = OreClusterStatus.valueOf( tag.getString("status") );

        if( this.id.equals(TEST_ID)) {
            int i = 0;
        }

        //Cluster Types
        {
            String clusterTypes = tag.getString("clusterTypes");

            this.clusterTypes = null;
            if(clusterTypes == null || clusterTypes.isEmpty()) {
                //add nothing
            }
            else
            {
                Map<Block,List<BlockPos>> clusters =  BlockUtil.deserializeBlockPairs(clusterTypes);
                this.clusterTypes = new HashMap<BlockState, BlockPos>();
                for(Map.Entry<Block, List<BlockPos>> entry : clusters.entrySet())
                {
                    BlockState blockState = entry.getKey().defaultBlockState();
                    List<BlockPos> positions = entry.getValue();
                    for(BlockPos pos : positions)
                    {
                        this.clusterTypes.put(blockState, pos);
                    }

                    if(this.clusterTypes.size() == 0)
                        this.clusterTypes.put(blockState, null);
                }
            }

            //LoggerProject.logDebug("003008", "Deserializing clusterTypes: " + clusterTypes);
        }

        //blockStateUpdates
        /*
        {
            String blockStateUpdates = tag.getString("blockStateUpdates");
            this.blockStateUpdates = new ConcurrentLinkedQueue<>();
            if(blockStateUpdates == null || blockStateUpdates.isEmpty()) {
               //add nothing
            }
            else {
                Map<Block,List<BlockPos>> blocks =  BlockUtil.deserializeBlockPairs(blockStateUpdates);
                for(Map.Entry<Block, List<BlockPos>> entry : blocks.entrySet())
                {
                    Block block = entry.getKey();
                    List<BlockPos> positions = entry.getValue();
                    for(BlockPos pos : positions)
                    {
                        this.blockStateUpdates.add( Pair.of(block, pos) );
                    }
                }
            }
            LoggerProject.logDebug("003009", "Deserializing blockStateUpdates: " + blockStateUpdates);
        }
        */

        OreClusterManager.addManagedOreClusterChunk( this );
    }

}
//END CLASS
