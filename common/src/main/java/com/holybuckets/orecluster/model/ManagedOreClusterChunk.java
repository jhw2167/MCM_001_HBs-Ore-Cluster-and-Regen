package com.holybuckets.orecluster.model;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkCapabilityProvider;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.OreClustersAndRegenMain;
import com.holybuckets.orecluster.core.OreClusterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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
    
    public static final String TEST_ID = "14,-6";


    public static enum ClusterStatus {
        NONE,
        DETERMINED,
        CLEANED,
        PREGENERATED,
        REGENERATED,
        GENERATED,
        HARVESTED,
        COMPLETE
    }

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedOreClusterChunk.class, () -> new ManagedOreClusterChunk(null) );
    }

    /** Variables **/
    private LevelAccessor level;
    private String id;
    private ChunkPos pos;
    private ClusterStatus status;
    private long timeLoaded;
    private boolean isReady;

    private HashMap<Block, BlockPos> clusterTypes;
    private Map<Block, HBUtil.Fast3DArray> originalOres;
    private ConcurrentLinkedQueue<Pair<Block, BlockPos>> blockStateUpdates;

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
        this.status = ClusterStatus.NONE;
        this.timeLoaded = System.currentTimeMillis();
        this.isReady = false;
        this.clusterTypes = null;
        this.blockStateUpdates = new ConcurrentLinkedQueue<>();

    }

    //One for building with id
    private ManagedOreClusterChunk(LevelAccessor level, String id)
     {
        this(level);
        this.id = id;
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

    public boolean hasChunk() {
        return getChunk(false) != null;
    }

    public ChunkPos getPos() {
        return pos;
    }

    public String getId() {
        return id;
    }

    public ClusterStatus getStatus() {
        return status;
    }

    @NotNull
    public HashMap<Block, BlockPos> getClusterTypes() {
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

    public Queue<Pair<Block, BlockPos>> getBlockStateUpdates() {
        return blockStateUpdates;
    }

    public Map<Block, HBUtil.Fast3DArray> getOriginalOres() {
        return originalOres;
    }

    public LevelAccessor getLevel() { return level; }

    public Long getTimeLoaded() { return timeLoaded; }

    public boolean isReady() { return isReady; }

    public Random getChunkRandom()
    {
      ManagedChunk parent = ManagedOreClusterChunk.getParent(level, id);
        if(parent == null)
            return null;

        return parent.getChunkRandom();
    }

    public synchronized ReentrantLock getLock() {
        return lock;
    }

    /** Setters **/

    public void setPos(ChunkPos pos) {
        this.pos = pos;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setStatus(ClusterStatus status) {
        this.status = status;
    }


    public void setOriginalOres(Map<Block, HBUtil.Fast3DArray> originalOres) {
        this.originalOres = originalOres;
    }

    public void setReady(boolean ready) {
        this.isReady = ready;
    }

    /**
     * Updates time loaded with the current system time in milliseconds
     */
    public void updateTimeLoaded() {
        this.timeLoaded = System.currentTimeMillis();
    }

    /** Other Methods **/
    public void addClusterTypes(List<Block> clusters)
    {
        if( clusters == null )
            return;
        Map<Block, BlockPos> clusterMap = new HashMap<>();
        for(Block block : clusters) {
            clusterMap.put(block, null);
        }
        this.addClusterTypes(clusterMap);
    }

    public void addClusterTypes(Map<Block, BlockPos> clusterMap)
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

    public void addBlockStateUpdate(Block block, BlockPos pos) {
        this.blockStateUpdates.add( Pair.of(block, pos) );
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

        if(this.status == ClusterStatus.HARVESTED)
            return true;

        if( this.status != ClusterStatus.GENERATED )
            return false;


        //If any block in the chunk does not equal a block in block state updates, set the chunk as harvested
        for(Pair<Block, BlockPos> pair : this.blockStateUpdates)
        {
            Block block = pair.getLeft();
            BlockPos pos = pair.getRight();
            if(!chunk.getBlockState(pos).getBlock().equals( block ))
            {
                LoggerProject.logDebug("003011", "Cluster Harvested: " + BlockUtil.positionToString(pos));
                this.status = ClusterStatus.HARVESTED;
                blockStateUpdates.clear();
                return true;
            }
        }

        return false;
    }

    public boolean hasBlockUpdates() {
        return this.blockStateUpdates != null && this.blockStateUpdates.size() > 0;
    }



    /** OVERRIDES **/

    @Override
    public ManagedOreClusterChunk getStaticInstance(LevelAccessor level, String id)
    {
        if(id == null || level == null )
         return null;

        OreClusterManager manager = OreClustersAndRegenMain.ORE_CLUSTER_MANAGER_BY_LEVEL.get(level);
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

    @Override
    public void handleChunkLoaded(ChunkEvent.Load event) {
        this.level = event.getLevel();
        OreClusterManager.onChunkLoad(event, this);
    }

        public void handleChunkLoaded() {
            OreClusterManager.onChunkLoad( level, this);
        }

    @Override
    public void handleChunkUnloaded(ChunkEvent.Unload event)
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
        return ManagedChunk.getManagedChunk(level, id);
    }

    public  static boolean isNoStatus(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.NONE;
    }

    public static boolean isDetermined(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.DETERMINED;
    }

    public static boolean isCleaned(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.CLEANED;
    }

    public static boolean isPregenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.PREGENERATED;
    }

    public static boolean isRegenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.REGENERATED;
    }

    public static boolean isGenerated(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.GENERATED;
    }

    public static boolean isHarvested(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.HARVESTED;
    }

    public static boolean isComplete(ManagedOreClusterChunk chunk) {
        return chunk.getStatus() == ClusterStatus.COMPLETE;
    }

    public static boolean isReady(ManagedOreClusterChunk chunk) {
        return chunk.isReady;
    }

    public static boolean isLoaded(ManagedOreClusterChunk chunk) {
        return ManagedChunk.isLoaded( chunk.getLevel(), chunk.getId() );
    }


    /** SERIALIZERS **/

    @Override
    public CompoundTag serializeNBT()
    {
        //LoggerProject.logDebug("003002", "Serializing ManagedOreClusterChunk");

        CompoundTag details = new CompoundTag();
        details.putString("id", this.id);

        if( this.id.equals(TEST_ID)) {
            int i = 0;
        }


        if( this.hasBlockUpdates() )
            details.putString("status", ClusterStatus.DETERMINED.toString());
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
                this.clusterTypes.keySet().forEach((k) -> clusters.put(k, new ArrayList<>()));
                for(Map.Entry<Block, BlockPos> entry : this.clusterTypes.entrySet())
                {
                    Block block = entry.getKey();
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

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        LoggerProject.logDebug("003003", "Deserializing ManagedOreClusterChunk");
        if(tag == null || tag.isEmpty())
            return;

        this.id = tag.getString("id");
        this.status = ClusterStatus.valueOf( tag.getString("status") );

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
                this.clusterTypes = new HashMap<>();
                for(Map.Entry<Block, List<BlockPos>> entry : clusters.entrySet())
                {
                    Block block = entry.getKey();
                    List<BlockPos> positions = entry.getValue();
                    for(BlockPos pos : positions)
                    {
                        this.clusterTypes.put(block, pos);
                    }

                    if(this.clusterTypes.size() == 0)
                        this.clusterTypes.put(block, null);
                }
            }

            LoggerProject.logDebug("003008", "Deserializing clusterTypes: " + clusterTypes);
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

        this.handleChunkLoaded();

    }



}
//END CLASS
