package com.holybuckets.orecluster.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.util.stream.Collectors;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.orecluster.LoggerProject;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.apache.commons.lang3.tuple.Pair;

public class OreClusterCalculator {

    public static final String CLASS_ID = "003";    //value used in logs

    private OreClusterManager manager;
    private ModRealTimeConfig C;
    private Set<String> determinedChunks;
    private ConcurrentHashMap<BlockState, Set<String>> existingClustersByType;



     //Constructor
    public OreClusterCalculator( final OreClusterManager manager)
    {
        this.manager = manager;
        this.C = manager.getConfig();
        this.determinedChunks = manager.getDeterminedChunks();
        this.existingClustersByType = manager.getExistingClustersByType();
    }

    public Map<String, List<BlockState>> calculateClusterLocations(List<String> chunks, Random rng)
    {
        //long startTime = System.nanoTime();

        // Get list of all ore cluster types
        Map<BlockState, OreClusterConfigModel> clusterConfigs = C.getOreConfigs();
        List<BlockState> oreClusterTypes = new ArrayList<>(clusterConfigs.keySet());

        HashMap<BlockState, Integer> clusterCounts = new HashMap<>();

        //Determine the expected total for each cluster type for this MAX_CLUSTERS batch
        // Use a normal distribution to determine the number of clusters for each type
        for (BlockState oreType : oreClusterTypes)
        {
            int normalizedSpawnRate = clusterConfigs.get(oreType).oreClusterSpawnRate;
            double sigma = ModRealTimeConfig.CHUNK_DISTRIBUTION_STDV_FUNC.apply(normalizedSpawnRate);
            int numClusters = (int) Math.round(rng.nextGaussian() * sigma + normalizedSpawnRate);

            clusterCounts.put(oreType, numClusters);
        }

        long step1Time = System.nanoTime();

        /** Add all clusters, distributing one cluster type at a time
        *
         *  Summarize the below implementation
         *  1. Get list of ids for recently (previously) loaded chunks, we need to check which have clusters so we don't place new ones too close!
         *  2. Build 2D Array of chunk positions that serves as a map identifying where current and previous clusters are
         *  3. Determine distribution of clusters as aggregate group over all chunks
         *      - e.g. 47 clusters over 256 chunks ~ 256/47 = 5.4 chunks per each cluster
         *      - Gaussian distribute the clusters between prevClusterPosition + ~N(5.4, minSpacing/3)
         *
         *   4. Using the Map of aggregate clusters, pick chunks for each cluster type
         */

         //1. Get recently loaded chunks
         String startChunk =  chunks.get(0);
         int minSpacing = C.getDefaultConfig().minChunksBetweenOreClusters;

        /** If the spacing between clusters is large, there will be fewer cluster chunks, so we can check all against
        *   all existing clusters instead of calculating the area around each chunk
        *   If the spacing is small, we will have many cluster chunks, better to check the radius
         */
         final int MIN_SPACING_VALIDATOR_CUTOFF_RADIUS = Math.min( determinedChunks.size(), (int) Math.pow(minSpacing, 2) );
         LinkedHashSet<String> chunksInRadiusOfStart = getChunkIdsInRadius(startChunk,
          Math.min( minSpacing, MIN_SPACING_VALIDATOR_CUTOFF_RADIUS ));
         String closestToCenter = chunksInRadiusOfStart.stream().min(Comparator.comparingInt( c ->
             Math.round( ChunkUtil.chunkDist( c, "0,0" ) )
         )).get();


        //2. Determine area needed for spiral generation of recent chunks
         int batchDimensions = (int) Math.ceil( Math.sqrt( chunks.size() ) );
         int spiralRadius = batchDimensions + MIN_SPACING_VALIDATOR_CUTOFF_RADIUS;

        //3. Stream existingClustersByType into a linkedHashSet, filtering for any chunks that have clusters
        LinkedHashSet<String> localExistingClusters = new LinkedHashSet<>();
        existingClustersByType.values().stream()
            .forEach( ids -> localExistingClusters.addAll(ids));
         


         int minX, minZ, maxX, maxZ;
         minX = minZ = maxX = maxZ = 0;

         for(String id : localExistingClusters )
         {
                ChunkPos pos = ChunkUtil.getPos(id);
                if( pos.x < minX )
                    minX = pos.x;
                if( pos.x > maxX )
                    maxX = pos.x;
                if( pos.z < minZ )
                    minZ = pos.z;
                if( pos.z > maxZ )
                    maxZ = pos.z;
          }


        //long step2Time = System.nanoTime();
        //LoggerBase.logDebug("Step 2 (Get recently loaded chunks and determine local existing clusters) took " + LoggerBase.getTime(step1Time, step2Time) + " ms");

        //3. Determine distribution of clusters as aggregate group over all chunks
        float totalClusters = clusterCounts.values().stream().mapToInt( i -> i ).sum();
        float chunksPerCluster = chunks.size() / totalClusters;
        float stdDev = Math.max( (chunksPerCluster - minSpacing) / 3, 0);

        List<String> chunksToBePopulated = new LinkedList<>();  //may contain duplicates


        int chunkIndex = 0;
        while( chunkIndex < chunks.size() )
        {
            chunkIndex = (int) Math.round(rng.nextGaussian() * stdDev + chunksPerCluster) + chunkIndex;
            chunkIndex = (chunkIndex < 0) ? 0 : chunkIndex;
            boolean openSpaceForCluster = false;
            while ( !openSpaceForCluster && chunkIndex < chunks.size() )
            {
                openSpaceForCluster = true;

                if( minSpacing == 0)
                    continue;

                /**
                 * In later stages of cluster generation, there will be overlap between
                 * the area we are attempting to generate and already populated clusters
                 * we tacitly "accept" a cluster in any chunk that already exists;
                 * these clusters, once assigned a particular ore type, will be discarded later
                 */

                String chunkId = chunks.get(chunkIndex++);
                if( determinedChunks.contains(chunkId) )
                    continue;

                /**
                 * If the radius within we are placing clusters is reasonably small, we can
                 * check each space within the radius to see if it is occupied by a cluster
                 *  ELSE
                 * The radius is too large and we will check all against all existing clusters instead
                 */

                if( minSpacing < MIN_SPACING_VALIDATOR_CUTOFF_RADIUS )
                {

                    //Now we found a chunk where we randomly want to place a cluster, check 2D array to check validity
                    LinkedHashSet<String> nearbyChunks = getChunkIdsInRadius(chunkId, minSpacing);
                    for( String nearbyChunk : nearbyChunks ) {
                        if( localExistingClusters.contains(nearbyChunk) ) {
                            openSpaceForCluster = false;
                            break;
                        }
                    }
                }
                else
                {
                    if( localExistingClusters.stream().anyMatch( c ->
                            ChunkUtil.chunkDist( c, chunkId ) < minSpacing ))
                    {
                        openSpaceForCluster = false;
                    }

                }

            }
            //FOUND A VALID CHUNK, PLACE A CLUSTER
            if( chunkIndex < chunks.size() )
            {
                String chunkId = chunks.get(chunkIndex);
                chunksToBePopulated.add(chunkId);
                localExistingClusters.add(chunkId);
            }

        }
        //END cluster placing algorithm
        //LoggerBase.logDebug("3. Cluster Placement Algorithm Complete");
        //LoggerBase.logDebug(chunksToBePopulated.toString());

        //long step3Time = System.nanoTime();
        //LoggerBase.logDebug("Step 3 (Determine distribution of clusters) took " + LoggerBase.getTime(step2Time, step3Time) + " ms");

        //4. Using the Map of aggregate clusters, pick chunks for each cluster type

        // Maps <ChunkId, <OreType, BlockPos>>
        HashMap<String, List<BlockState>> clusterPositions = new HashMap<>();

        //Order OreCluster types by spawnRate ascending
        oreClusterTypes.sort(Comparator.comparingInt( o -> -1*clusterConfigs.get(o).oreClusterSpawnRate ));
        LinkedHashSet<String> selectedChunks = new LinkedHashSet<>();

        /**
         * Iterate over all ore types we want to place clusters for
         * 1. Obtain configs for the ore type
         * 2. Get all existing chunks in the world with the ore cluster type
         * 3. Remove all chunks from this list that are not in the local area
         *  (SKIP THIS STEP IN CASE OF LARGE RANGES)
         * 4. Remove set of available chunks that were selected from previous ore
         * 5. Initialize index variables for traversing "chunksTobePopulated"
         * 6. Calculate the nextIndex over all chunks to be populated
         * 7. If suggested "chunksToBePopulated.get(index)" chunk is already explored, skip it
         *      - We skip by autimatically accepting it, this cluster will be discarded later since this
         *      area was already assessed for chunks.
         * 8. Pop chunks from the list until we find a valid chunk after nextIndex
         *      - Validate no other cluster of the same type is within minSpacing
         * 9. Place the cluster by adding it to clusterPositions and selectedChunks
         *
         */
         try {


             for (BlockState oreType : oreClusterTypes)
             {
                 OreClusterConfigModel config = clusterConfigs.get(oreType);
                 HashSet<String> allChunksWithClusterType = existingClustersByType.get(oreType).stream().collect(Collectors.toCollection(HashSet::new));
                 //allChunksWithClusterType.removeIf( c -> !localExistingClusters.contains(c) );
                 final int MIN_SPACING_SPECIFIC_CLUSTER_VALIDATOR_CUTOFF_RADIUS = Math.min(allChunksWithClusterType.size(),
                     (int) Math.pow(config.minChunksBetweenOreClusters, 2));
                 //LoggerBase.logDebug("Validating clusters for ore type: " + oreType);
                 //LoggerBase.logDebug("Existing clusters for this ore type: " + allChunksWithClusterType.size());
                 //LoggerBase.logDebug("Min spacing for this ore type: " + MIN_SPACING_SPECIFIC_CLUSTER_VALIDATOR_CUTOFF_RADIUS);


                 int totalSpecificClusters = clusterCounts.get(oreType);
                 if( totalSpecificClusters == 0 )
                     continue;
                 //LoggerBase.logDebug("Total clusters for this ore type: " + totalSpecificClusters);

                 int clustersPlaced = 0;
                 int specificMinSpacing = config.minChunksBetweenOreClusters;

                 //removes all chunks that were selected by previous ores, removes item from hashset so duplicates are left
                 Iterator<String> it = chunksToBePopulated.iterator();
                 while (it.hasNext()) {
                     String chunkId = it.next();
                     if (selectedChunks.remove(chunkId)) {
                         it.remove();
                     }
                 }
                 LinkedList<String> chunksToBePopulatedSpecificCopy = new LinkedList<>(chunksToBePopulated);
                 Collections.shuffle(chunksToBePopulatedSpecificCopy, rng);
                 boolean validCluster = false;

                 while (clustersPlaced < totalSpecificClusters)
                 {
                     clustersPlaced++;
                     String candidateChunkId = null;
                     validCluster = false;

                     while (!validCluster && !chunksToBePopulatedSpecificCopy.isEmpty())
                     {
                         candidateChunkId = chunksToBePopulatedSpecificCopy.removeFirst();

                        //No clusters added to chunks that already exist, discarded
                         if (determinedChunks.contains(candidateChunkId))
                             break;

                         //Check if the chunk is within the radius of a chunk with the same cluster type
                         validCluster = true;
                         if (specificMinSpacing < MIN_SPACING_SPECIFIC_CLUSTER_VALIDATOR_CUTOFF_RADIUS) {
                             LinkedHashSet<String> nearbyChunks = getChunkIdsInRadius(candidateChunkId, specificMinSpacing);
                             for (String nearbyChunk : nearbyChunks) {
                                 if (allChunksWithClusterType.contains(nearbyChunk)) {
                                     validCluster = false;
                                     break;
                                 }
                             }
                         } else {
                             final String id = candidateChunkId;
                             if (allChunksWithClusterType.stream().anyMatch(c ->
                                 ChunkUtil.chunkDist(c, id) < specificMinSpacing)) {
                                 validCluster = false;
                             }
                         }

                     }
                     //END WHILE FIND VALID CHUNK FOR GIVEN CLUSTER

                     //PLACE THE CLUSTER
                     if (validCluster && candidateChunkId != null)
                     {
                         selectedChunks.add(candidateChunkId);
                         allChunksWithClusterType.add(candidateChunkId);
                         if (clusterPositions.containsKey(candidateChunkId))
                         {
                             clusterPositions.get(candidateChunkId).add(oreType);
                         }
                         else
                         {
                             LinkedList<BlockState> clusterMap = new LinkedList<>();
                             clusterMap.add(oreType);
                             clusterPositions.put(candidateChunkId, clusterMap);
                         }
                     }

                 }
                 //END WHILE PLACE ALL CLUSTERS OF THIS TYPE


             }
             //END FOR EACH ORE TYPE
         }
         catch (Exception e)
         {
            StringBuilder sb = new StringBuilder();
            clusterCounts.entrySet().forEach( ore -> sb.append(ore.getKey() + ": " + ore.getValue() + ", "));
            LoggerProject.logWarning("003001", "Unable to place all ore clusters: " + e.getMessage() + "Remaining Counts: " + sb.toString());
             e.printStackTrace();
         }

        long step4Time = System.nanoTime();
        //LoggerBase.logDebug("Step 4 (Pick chunks for each cluster type) took " + LoggerBase.getTime(step3Time, step4Time) + " ms");

        //6. Remove all clusters at chunks that were populated in previous batches
        Iterator<String> clusterPos =  clusterPositions.keySet().iterator();
        while( clusterPos.hasNext() ) {
            String chunkId = clusterPos.next();
            if( determinedChunks.contains(chunkId) )
                clusterPos.remove();
        }
        long step5Time = System.nanoTime();
        //LoggerBase.logDebug("Step 5 (Remove all clusters at chunks that were populated in previous batches) took " + LoggerBase.getTime(step4Time, step5Time) + " ms");


        long endTime = System.nanoTime();
        //LoggerBase.logDebug("Total time for calculateClusterLocations: " + LoggerBase.getTime(startTime, endTime) + " ms");

        return clusterPositions;
    }


        /**
         * Get a list of chunk ids within a given radius of a chunk - radius of 0 is itself
          * @param chunkId
         * @param radius
         * @return
         */
        private LinkedHashSet<String> getChunkIdsInRadius( String chunkId, int radius )
        {
            LinkedHashSet<String> chunks = new LinkedHashSet<>();
            ChunkPos center = ChunkUtil.getPos(chunkId);
            for( int x = center.x - radius; x <= center.x + radius; x++ )
            {
                for( int z = center.z - radius; z <= center.z + radius; z++ )
                {
                    chunks.add(ChunkUtil.getId(x, z));
                }
            }
            return chunks;
        }

    /** ######################
     *  END DETERMINE CLUSTERS
     *  ######################
     */

    /**
     * CLEAN CLUSTERS
     * @return boolean false if cleaning failed
     */
    public boolean cleanChunkFindAllOres(ManagedOreClusterChunk chunk, final Set<BlockState> COUNTABLE_ORES)
    {
        LevelChunk levelChunk = chunk.getChunk(false);
        if( levelChunk == null )
            return false;
        LevelChunkSection[] sections = levelChunk.getSections();

        final int SECTION_SZ = 16;

        int count = 0;
        int outerCount = 0;
        //loop in reverse, top, down
        final boolean TURN_OFF = false;
        for (int i = sections.length - 1; i >= 0; i--)
        {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || TURN_OFF)
                continue;

            //iterate over x, y, z
            PalettedContainer<BlockState> states = section.getStates();

            for (int x = 0; x < SECTION_SZ; x++)
            {
                for (int y = 0; y < SECTION_SZ; y++)
                {
                    for (int z = 0; z < SECTION_SZ; z++)
                    {
                        outerCount++;
                        BlockState blockState = states.get(x, y, z);
                        if (COUNTABLE_ORES.contains(blockState)) {
                          BlockState s = states.get(x, y, z);
                          if( chunk.sampleAddOre(s) ) {
                            HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
                              HBUtil.WorldPos pos = new HBUtil.WorldPos(relativePos, i, levelChunk);
                              chunk.addOre(s, pos.getWorldPos(), true);
                          }

                        }
                        //Else nothing
                    }
                    //LoggerProject.logDebug("002027","Finished x,y,z (" + x + "," + y +")");
                }
                //LoggerProject.logDebug("002027","Finished x: (" + x + ")");
            }
            //END 3D iteration

            //LoggerProject.logDebug("002028.5","Finished section: " + i);
        }
        //END SECTIONS LOOP

        //Print the oreVertices array
        //LoggerProject.logDebug("002028","Finished all sections for  " + chunk.getId() + " , found " + oreVerticesByBlock );
        return true;
    }


    public void cleanChunkSelectClusterPosition(ManagedOreClusterChunk chunk)
    {

        final Map<BlockState, BlockPos> CLUSTER_TYPES = chunk.getClusterTypes();

        for (BlockState b : CLUSTER_TYPES.keySet())
        {
            if( CLUSTER_TYPES.get(b) != null ) continue;
            BlockPos orePos = chunk.getOreClusterSourcePos(b);
            if( orePos != null ) CLUSTER_TYPES.put(b, orePos);
        }

    }

    /*/*
     * Clean the necessary ores in the chunk by replacing the blocks at the specified position
     * with the first ore provided in the oreClusterReplaceableEmptyBlocks array (for the particular ore config)
     * @param chunk
     * @param CLEANABLE_ORES
     *
    public void cleanChunkOres(ManagedOreClusterChunk chunk, Set<Block> CLEANABLE_ORES)
    {

        final Map<Block, OreClusterConfigModel> ORE_CONFIGS = C.getOreConfigs();
        final Random randSeqClusterBuildGen = chunk.getChunkRandom();

        for (Block b : CLEANABLE_ORES)
        {
            HBUtil.Fast3DArray oreVertices = oreVerticesByBlock.get(b);
            if (oreVertices == null)
                continue;

            Block[] replacements = ORE_CONFIGS.get(b).oreClusterReplaceableEmptyBlocks.toArray(new Block[0]);
            Float modifier = ORE_CONFIGS.get(b).oreVeinModifier;

            //need to replace 1-f blocks in the ores list with the first replacement block in the array
            for (int j = 0; j < oreVertices.size; j++) {
                //If we want mod ~ 0.8 (80% of ore to spawn) then 20% of the time we will replace the block
                if (randSeqClusterBuildGen.nextFloat() > modifier) {
                    BlockPos bp = new BlockPos(oreVertices.getX(j), oreVertices.getY(j), oreVertices.getZ(j));
                    chunk.addBlockStateUpdate(replacements[0], bp);
                }

            }
        }

    }
    */


    /** ######################
     *  END CLEAN CLUSTERS
     *  ######################
     */






    /**
     * Determine the source position of a cluster
     * @param oreType
     * @param chunk
     * @return
     */
    public Vec3i determineSourcePosition(String oreType, LevelChunk chunk) {
        return null;
    }


    /**
     * Takes an oreConfig and a source position and generates a sequence of positions
     * that will become the ore cluster in the world.
     *
     * This process is expensive and delaying load times
     *
     * @param source
     * @return List of BlockPos that make up the ore cluster
     */
    public List<BlockPos> generateCluster(Pair<BlockState, BlockPos> source)
    {

        //1. Determine the cluster size and shape
        //HBUtil.TripleInt volume = config.oreClusterVolume;
        //String shape = config.oreClusterShape;
        BlockState oreType = source.getLeft();
        BlockPos sPos = source.getRight();
        final OreClusterConfigModel config = C.getOreConfigs().get(oreType);

       //2. Generate Cube
       final TripleInt VOL = config.oreClusterVolume;
       //Fast3DArray positions = ShapeUtil.getCube(VOL.x, VOL.z, VOL.y);
        Fast3DArray positions = ShapeUtil.getCircle(VOL.x);


       //3. Convert cube to BlockPos
        List<BlockPos> blockPositions = new ArrayList<>();
        for( int i = 0; i < positions.size; i++ )
        {
            int x = sPos.getX() + positions.getX(i);
            int y = sPos.getY() + positions.getY(i);
            int z = sPos.getZ() + positions.getZ(i);
            blockPositions.add(new BlockPos(x, y, z));
        }

        return blockPositions;
    }
    //END GENERATE ORE CLUSTERS


}
//END CLASS

