package com.holybuckets.orecluster.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.orecluster.ModRealTimeConfig;
import com.holybuckets.orecluster.config.OreClusterConfigData;
import com.holybuckets.orecluster.core.model.ManagedOreClusterChunk;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import com.holybuckets.orecluster.config.model.OreClusterConfigModel;
import com.holybuckets.foundation.HBUtil.*;
import com.holybuckets.orecluster.LoggerProject;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.apache.commons.lang3.tuple.Pair;

import static com.holybuckets.orecluster.config.model.OreClusterConfigModel.OreClusterId;

public class OreClusterCalculator {

    public static final String CLASS_ID = "003";    //value used in logs

    private OreClusterManager manager;
    private Level level;
    private ModRealTimeConfig C;
    private Set<String> determinedChunks;
    private ConcurrentHashMap<OreClusterId, Set<String>> localAreaClustersByType;



     //Constructor
    public OreClusterCalculator( final OreClusterManager manager)
    {
        this.manager = manager;
        this.level = manager.getLevel();
        this.C = manager.getConfig();
        this.determinedChunks = manager.getDeterminedChunks();
        this.localAreaClustersByType = manager.getTentativeClustersByType();
    }

    /**
     *
     * @param chunks
     * @param rng
     * @return
     * determineClusters
     * determineChunks
     */
    @Deprecated
    public Map<String, List<OreClusterId>> calculateClusterLocations(List<String> chunks, Random rng)
    {
        //long startTime = System.nanoTime();

        // Get list of all ore cluster types
        Map<OreClusterId, OreClusterConfigModel> clusterConfigs = new HashMap<>();
        for( OreClusterId oreType : C.getOreConfigs().keySet() )
        {
            OreClusterConfigModel config = C.getOreConfigs().get(oreType);
            if( C.clustersDoSpawn(config) && C.doesLevelMatch(config, level) )
                clusterConfigs.put(oreType, config);
        }

        HashMap<String, List<OreClusterId>> clusterPositions = new HashMap<>();
        List<OreClusterId> oreClusterTypes = new ArrayList<>(clusterConfigs.keySet());
        if(oreClusterTypes.isEmpty()) {
            for( String chunkId : chunks ) {
                clusterPositions.put(chunkId, null);
            }
            return clusterPositions;
        }

        HashMap<OreClusterId, Integer> clusterCounts = new HashMap<>();

        //Determine the expected total for each cluster type for this MAX_CLUSTERS batch
        // Use a normal distribution to determine the number of clusters for each type
        for (OreClusterId oreType : oreClusterTypes)
        {
            int normalizedSpawnRate = clusterConfigs.get(oreType).oreClusterSpawnRate;
            if( normalizedSpawnRate == 0 ) continue;
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
         int minSpacing = C.getDefaultConfigModel().minChunksBetweenOreClusters;

        /* If the spacing between clusters is large, there will be fewer cluster chunks, so we can check all against
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
        localAreaClustersByType.values().stream()
            .forEach( ids -> localExistingClusters.addAll(ids));
         


         int minX, minZ, maxX, maxZ;
         minX = minZ = maxX = maxZ = 0;

         for(String id : localExistingClusters )
         {
                ChunkPos pos = ChunkUtil.getChunkPos(id);
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

        //4. Using the Map of aggregate clusters, pick chunks for each cluster type
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


             for (OreClusterId oreType : oreClusterTypes)
             {
                 OreClusterConfigModel config = clusterConfigs.get(oreType);
                 HashSet<String> allChunksWithClusterType = localAreaClustersByType.get(oreType).stream().collect(Collectors.toCollection(HashSet::new));
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
                             LinkedList<OreClusterId> clusterMap = new LinkedList<>();
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
            LoggerProject.logWarning("013001", "Unable to place all ore clusters: " + e.getMessage() + "Remaining Counts: " + sb.toString());
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
            ChunkPos center = ChunkUtil.getChunkPos(chunkId);
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
                        BlockState blockState = states.get(x, y, z).getBlock().defaultBlockState();
                        Biome localBiome = section.getNoiseBiome(x, 0, z).value();
                        chunk.addBiome(localBiome);
                        if (COUNTABLE_ORES.contains(blockState))
                        {
                          OreClusterId id = chunk.chooseConfigId(level, localBiome, blockState, C);
                          if(id == null) continue;
                          int sectionY = level.getSectionYFromSectionIndex(i);

                          if( chunk.sampleAddOre(id, sectionY ) ) {
                              HBUtil.TripleInt relativePos = new HBUtil.TripleInt(x, y, z);
                              HBUtil.WorldPos pos = new HBUtil.WorldPos(relativePos, i, levelChunk);
                              chunk.addOre(id, pos.getWorldPos(), true);
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

        final Map<OreClusterId, BlockPos> CLUSTER_TYPES = chunk.getClusterTypes();

        for (OreClusterId b : CLUSTER_TYPES.keySet())
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
     * Takes an oreConfig and a source position and generates a sequence of positions
     * that will become the ore cluster in the world.
     *
     * This process is expensive and delaying load times
     * @param clusterId source type of the cluster
     * @param sourcePos source position of the cluster
     * @return List of Blockstate - BlockPos pairs that make up the ore cluster
     */
    public List<Pair<BlockState, BlockPos>> generateCluster(ManagedOreClusterChunk chunk, OreClusterId clusterId, BlockPos sourcePos)
    {

        //1. Determine the cluster size and shape
        //HBUtil.TripleInt volume = config.oreClusterVolume;
        final OreClusterConfigModel config = C.getOreConfigs().get(clusterId);

       //2. Generate Cube
       final TripleInt VOL = config.oreClusterVolume;
       String SHAPE = config.oreClusterShape;

        int randSeed = HBUtil.BlockUtil.mapTo1DNumber( sourcePos );
        List<String> validShapes = OreClusterConfigData.COreClusters.DEF_ORE_CLUSTER_VALID_SHAPES.stream().toList();
        if( SHAPE.equals("ANY") || SHAPE.equals("NONE") ) {
            int offset = validShapes.size();
            SHAPE = validShapes.get( randSeed % offset );
        }

        Fast3DArray positions;
       if( SHAPE.equals("SPHERE") ) {
            int radius = Math.min(VOL.x, VOL.z);
           positions = ShapeUtil.getSphere(radius, VOL.y);
       }
       else //(SHAPE.equals("CUBE"))
       {
            positions = ShapeUtil.getCube(VOL.x, VOL.z, VOL.y);
       }




       //3. Convert cube to BlockPos
        List<BlockPos> blockPositions = new ArrayList<>(positions.size);
        List<Integer> Xs = new ArrayList<>(positions.size);
        List<Integer> Ys = new ArrayList<>(positions.size);
        List<Integer> Zs = new ArrayList<>(positions.size);
        for( int i = 0; i < positions.size; i++ )
        {
            Xs.add(positions.getX(i)); Ys.add(positions.getY(i)); Zs.add(positions.getZ(i));
            int x = sourcePos.getX() + positions.getX(i);
            int y = sourcePos.getY() + positions.getY(i);
            int z = sourcePos.getZ() + positions.getZ(i);
            blockPositions.add(new BlockPos(x, y, z));
        }

        //4. Determine if we are near an open cave and which side it is on
        //LoggerProject.logInfo("013009", "Determining air offset" );
        List<Pair<Integer,List<Integer>>> relativePosData = new ArrayList<>();
        relativePosData.add(Pair.of(VOL.x, Xs));
        relativePosData.add(Pair.of(VOL.y, Ys));
        relativePosData.add(Pair.of(VOL.z, Zs));

        OreClusterGeneratorUtility generator;
        try {
             generator = new OreClusterGeneratorUtility(chunk, clusterId, blockPositions, relativePosData);
        } catch ( NullPointerException e) {
            return null;
        }

        TripleInt airOffset = new TripleInt(0,0,0);
        if( HBUtil.BlockUtil.blockToString(clusterId.getBlock()).toLowerCase().contains("ore") )
            airOffset = generator.calculateAvoidAirOffset();

        //5. Apply Density function
        List<BlockState> clusterBlockStates = generator.applyRadialDensityFunction();
        if( clusterBlockStates == null ) return null;

        //6. Apply translation(s):
        List<Pair<BlockState, BlockPos>> clusterBlockStatePositions = new ArrayList<>(positions.size);
        for(int i = 0; i < clusterBlockStates.size(); i++)
        {
            if(clusterBlockStates.get(i) == null) continue;

            BlockState state = clusterBlockStates.get(i);
            BlockPos pos = blockPositions.get(i).offset(airOffset.x, airOffset.y, airOffset.z);
            clusterBlockStatePositions.add(Pair.of(state, pos));
        }

        //7. Remove any block update that alter a nonReplaceable block
        //Iterate over clusterBlockStatePositions and use level.getBlockState to check if the block is non-replaceable
        var it = clusterBlockStatePositions.iterator();
        while (it.hasNext())
        {
            Pair<BlockState, BlockPos> pair = it.next();
            if(pair == null || pair.getRight() == null) continue;
            BlockState defaultBlockState = level.getBlockState(pair.getRight()).getBlock().defaultBlockState();
            if( config.oreClusterNonReplaceableBlocks.contains(defaultBlockState) ) {
                it.remove();
            }
        }


        return clusterBlockStatePositions;
    }
    //END GENERATE ORE CLUSTERS


    class OreClusterGeneratorUtility
    {

        private ManagedOreClusterChunk chunk;
        private LevelChunk levelChunk;
        private Random randomGenerator;
        private List<BlockPos> blockWorldPositions;
        private List<Pair<Integer, List<Integer>>> relativePositions;

        private OreClusterId oreClusterId;
        private OreClusterConfigModel config;
        private TripleInt volume;
        private float expectedOreDensity;
        private List<BlockState> proportionedBlockStates;

        //Densities
        private Holder<NormalNoise.NoiseParameters> noiseParametersHolder;
        private DensityFunction.NoiseHolder noiseHolder;
        private DensityFunction noise;

        private DensityFunction linearXDensity;
        private DensityFunction linearZDensity;
        private DensityFunction linearYDensity;
        private DensityFunction linearXZDensity;
        private DensityFunction radialXZDensity;
        private DensityFunction radialZXDensity;

        private DensityFunction.ContextProvider densityFunctionContext;


        OreClusterGeneratorUtility(ManagedOreClusterChunk chunk, OreClusterId id, List<BlockPos> blockPositions, List<Pair<Integer, List<Integer>>> positions)
        {
            this.chunk = chunk;
            this.levelChunk = this.chunk.getChunk(false);
            this.randomGenerator = this.chunk.getChunkRandom();
            if( levelChunk == null ) throw new NullPointerException("LevelChunk for ManagedOreClusterChunk " + chunk.getId() + " is null" );
            this.blockWorldPositions = blockPositions;
            this.relativePositions = positions;

            //Config
            this.oreClusterId = id;
            this.config = C.getOreConfigModel(id);

            this.volume = config.oreClusterVolume;
            this.expectedOreDensity = config.oreClusterDensity;

            this.initOreDensityPortions();
            this.initDensityFunctions();
        }


        /**
         * Returns advisement to offset cluster best to avoid spawning in open air where cluster may look unnatural
         * @return
         */
        TripleInt calculateAvoidAirOffset()
        {

            //4. Determine "edges" of the cluster as all positions at the top, bottom, left, right etc. 20% of the shape
            final float EDGE_PORTION_CONSTANT = 0.2f;
            final Function<Integer,Integer> CALC_EDGE_SZ = (Integer size) -> (int) Math.ceil( size * EDGE_PORTION_CONSTANT );
            List<Float> clusterEdgeAirPortions = new ArrayList<>(6);

            List<BlockPos> nearEdges = new ArrayList<>();
            List<BlockPos> farEdges = new ArrayList<>();

            //Determine edge portion as air for each side (as float)
            for(Pair<Integer, List<Integer>> dimension : relativePositions)
            {
                if(dimension.getLeft() == 0) continue;
                nearEdges.clear(); farEdges.clear();

                int dimLength = dimension.getLeft();
                int EDGE_SZ = CALC_EDGE_SZ.apply(dimension.getLeft());
                int NEAR_CUTOFF = dimLength - EDGE_SZ;
                int FAR_CUTOFF = -1*dimLength + EDGE_SZ;

                List<Integer> relativePos = dimension.getRight();
                for (int i = 0; i < relativePos.size(); i++)
                {
                    if( relativePos.get(i) >= NEAR_CUTOFF)         //most positive
                        nearEdges.add(blockWorldPositions.get(i));
                    else if( relativePos.get(i) <= FAR_CUTOFF )   //more negative
                        farEdges.add(blockWorldPositions.get(i));
                }
                clusterEdgeAirPortions.add(checkSideAirExposure(nearEdges));
                clusterEdgeAirPortions.add(checkSideAirExposure(farEdges));

            }


            final TripleInt OFFSET = new TripleInt(0, 0, 0);
            for(int i = 0; i < clusterEdgeAirPortions.size(); i+=2 )
            {
                int EDGE_SZ = CALC_EDGE_SZ.apply(relativePositions.get(i/2).getLeft());
                Function<Float, Integer> getOffset = (Float portion) -> {
                    if(portion < 0.2f) return 0;
                    else if(portion < 0.4f) return (int) Math.ceil(EDGE_SZ * 0.5f);
                    else if(portion < 0.6f) return (int) Math.ceil(EDGE_SZ * 1f);
                    else if(portion < 0.8f) return (int) Math.ceil(EDGE_SZ * 1.5f);
                    else return (int) Math.ceil(portion * 2f);
                };

                int offset = 0;
                int nearOffset = getOffset.apply(clusterEdgeAirPortions.get(i));
                if( nearOffset > 0) offset -= nearOffset;

                int farOffset = getOffset.apply(clusterEdgeAirPortions.get(i+1));
                if( farOffset > 0) offset += farOffset;

                if( i < 2 )
                    OFFSET.x  = offset;
                else if( i < 4 )
                    OFFSET.y = offset;
                else
                    OFFSET.z = offset;

            }

            return OFFSET;
        }


        /**
         * For all provided blockPos checks if the position occupies air
         * @param positions
         * @return
         */
        private float checkSideAirExposure(List<BlockPos> positions)
        {
            if( positions == null || positions.isEmpty() ) return 0;

            int count = 0;
            for (BlockPos pos : positions) {
                if (levelChunk.getBlockState(pos).isAir()) {
                    count++;
                }
            }

            return count / (float) positions.size();
        }


        //Add cutoff values to list which determine dynamically which density values map to which blocks
        private void initOreDensityPortions()
        {
            List<BlockState> temp = new ArrayList<>(100);
            int density = (int) (this.expectedOreDensity * 100);

            //Do our best to match the blockState of block at the cluster source position, but sometimes this will
            //just be a random block and we don't want to convert our cluster to a stone or grass cluster
            BlockPos clusterOrigin = chunk.getOreClusterSourcePos(oreClusterId);
            Block blockAtClusterOrigin = levelChunk.getBlockState(clusterOrigin).getBlock();
            BlockState baseBlockState = config.oreClusterType;
            if(config.oreClusterType.getBlock().equals(blockAtClusterOrigin))
                baseBlockState = levelChunk.getBlockState(clusterOrigin);

            for( int i = 0; i < 100; i++ ) {
                temp.add(baseBlockState);
            }
            List<BlockState> alternativeBlocks = config.oreClusterReplaceableEmptyBlocks.stream().toList();
            int partition = (int) ( ((float) (100 - density)) / alternativeBlocks.size());
            //Add the rest in even portions
            int pos = density;
            for( int i = 0; i < alternativeBlocks.size(); i++ ) {
                for( int j = 0; j < partition; j++ ) {
                    temp.set(pos++, alternativeBlocks.get(i));
                    if( pos >= 100 ) break;
                }
            }

            BlockState[] states = new BlockState[201];
            //mirror temp and fill proportionedBlockStates from the center at 100, out
            for( int i = 0; i < 100; i++ ) {
                states[100-i] = temp.get(i);
                states[100+i] = temp.get(i);
            }
            this.proportionedBlockStates = Arrays.asList(states);

        }


        private void initDensityFunctions()
        {
            final long seed = this.randomGenerator.nextLong();
            final int FIRST_OCTAVE = -2;
            final List<Double> AMPLITUDES = Arrays.asList(1.0, 0.5, 0.25, 0.125); // Perlin layers
            NormalNoise.NoiseParameters nParameters = new NormalNoise.NoiseParameters(FIRST_OCTAVE, AMPLITUDES);
            RandomSource rSource = RandomSource.create(seed); // Fixed seed for deterministic behavior
            NormalNoise perlinNoise = NormalNoise.create(rSource, nParameters);
            UniformNoiseFunction uniformNoise = new UniformNoiseFunction(seed);

            int clusterVolume = volume.x * volume.y * volume.z;
            if (clusterVolume < 144) {
                noise = uniformNoise;
            }
            else {
                double scaleFactor = clusterVolume < 168 ? 1.0 : Math.min(Math.sqrt(clusterVolume) / 10.0, 2.0);

                // Primary Perlin noise to introduce clustering
                DensityFunction perlinDensity = new CustomNoiseFunction(
                    perlinNoise,
                    0.1 * scaleFactor,  // Adjust XZ scale dynamically
                    1.0 * scaleFactor   // Adjust vertical scale dynamically
                );
                // Normalize Perlin noise from [-1,1] to [0,1]
                DensityFunction normalizedPerlin = perlinDensity;

                DensityFunction constant_p = DensityFunctions.constant(1.0);   // Slightly lower noise weight
                noise =  mul( DensityFunctions.add( normalizedPerlin, uniformNoise), constant_p ).clamp(-1d,1d);
            }
            //noise =

            // Falloffs with raw coordinate ranges
            // Dynamic falloffs
            int halfX = volume.x / 2;
            int halfY = volume.y / 2;
            int halfZ = volume.z / 2;
            DensityFunction linearXFalloff = DensityFunctions.yClampedGradient(-halfX, halfX, 0.0, 1.0);
            DensityFunction linearZFalloff = DensityFunctions.yClampedGradient(-halfZ, halfZ, 0.0, 1.0);
            DensityFunction linearYFalloff = DensityFunctions.yClampedGradient(-halfY, halfY, 0.0, 1.0);
            DensityFunction radialXZFalloff = new RadialGradient(new TripleInt(0, 0, 0), Math.max(volume.x, volume.z) / 2.0, 0.0, 1.0);
            DensityFunction radialZXFalloff = new RadialGradient(new TripleInt(0, 0, 0), Math.max(volume.x, volume.z) / 2.0, 0.0, 1.0);

            this.densityFunctionContext = new DensityFunction.ContextProvider() {
                @Override
                public DensityFunction.FunctionContext forIndex(int index) {
                    TripleInt p = getRelativePos(index);
                    return new DensityFunction.SinglePointContext(p.x, p.y, p.z);
                }

                @Override
                public void fillAllDirectly(double[] values, DensityFunction density) {
                    for (int i = 0; i < values.length; i++) {
                        TripleInt p = getRelativePos(i);
                        DensityFunction.FunctionContext context = new DensityFunction.SinglePointContext(p.x, p.y, p.z);
                        values[i] = density.compute(context);
                    }
                }
            };
        }

        private TripleInt getRelativePos(int i) {
            return new TripleInt(
                relativePositions.get(0).getRight().get(i), // No abs
                relativePositions.get(1).getRight().get(i),
                relativePositions.get(2).getRight().get(i)
            );
        }


        private DensityFunction mul( DensityFunction d1, DensityFunction d2 ) {
            return DensityFunctions.mul(d1, d2);
        }

        /**
         * Applies radial density function to BlockPositions Array
         * @return
         */
        List<BlockState> applyRadialDensityFunction()
        {
            List<BlockState> blockStates = new ArrayList<>(blockWorldPositions.size());
            List<BlockState> blockStatesXZ = new ArrayList<>(blockWorldPositions.size());
            List<BlockState> blockStatesZX = new ArrayList<>(blockWorldPositions.size());

            int size = blockWorldPositions.size();
            //Integer[] Xs = relativePositions.get(0).getRight().toArray(new Integer[0]);
            //Integer[] Ys = relativePositions.get(1).getRight().toArray(new Integer[0]);
            //Integer[] Zs = relativePositions.get(2).getRight().toArray(new Integer[0]);

            //XZ Results
            for( int i = 0; i < size; i++ ) {
                //blockStatesXZ.add(i, getBlockState(Xs[i], Ys[i], Zs[i], radialXZDensity));
            }

            //ZX Results
            for( int i = 0; i < size; i++ ) {
                //blockStatesZX.add(i, getBlockState(Zs[i], Ys[i], Xs[i], radialZXDensity));
            }

            //When abs(x) > abs(z), use XZ
            //When equal, 50/50
            //When abs(z) > abs(x), use ZX
            for( int i = 0; i < size; i++ )
            {
                /*
                if( Math.abs(Xs[i]) > Math.abs(Zs[i]) )
                    blockStates.add(blockStatesXZ.get(i));
                else if( Math.abs(Xs[i]) < Math.abs(Zs[i]) )
                    blockStates.add(blockStatesZX.get(i));
                else
                    blockStates.add(randomGenerator.nextBoolean() ? blockStatesXZ.get(i) : blockStatesZX.get(i));
                */

                 blockStates.add( i, getBlockState(i, noise) );
            }

            double[] sample = new double[size];
            densityFunctionContext.fillAllDirectly(sample, noise);

            return blockStates;
        }


        private BlockState getBlockState(int i, DensityFunction density) {
          TripleInt pos = getRelativePos(i);
          return getBlockState(pos.x, pos.y, pos.z, density);
        }

        private BlockState getBlockState(int x, int y, int z, DensityFunction density) {
            DensityFunction.SinglePointContext pos = new DensityFunction.SinglePointContext(x, y, z);
            double densityVal = density.compute(pos);
            int index = (int) (densityVal * 99) + 100; //centered at 100
            return proportionedBlockStates.get(index);
            //noise =
        }

        private int abs(Number a) {
            return Math.abs(a.intValue());
        }

    }
    //END INNER CLASS OreClusterGeneratorUtility

    public class UniformNoiseFunction implements DensityFunction.SimpleFunction
    {
        private final long seed;

        public UniformNoiseFunction(long seed) {
            this.seed = seed;
        }

        @Override
        public double compute(FunctionContext context) {
            long hash = hashCoords(context.blockX(), context.blockY(), context.blockZ());
            double noise = (hash & 0x7FFFFFFF) / (double) Integer.MAX_VALUE; // [0, 1)
            return (noise*2) - 1;
        }

        @Override
        public double minValue() {
            return -1.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        private long hashCoords(int x, int y, int z) {
            long h = seed;
            h = h * 31 + x;
            h = h * 31 + y;
            h = h * 31 + z;
            return Mth.murmurHash3Mixer(Long.valueOf(h).intValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            // Simplified codec; in a full implementation, you'd serialize parameters
            return KeyDispatchDataCodec.of(MapCodec.unit(this));

        }
    }



    class CustomNoiseFunction implements DensityFunction.SimpleFunction
    {

        private final NormalNoise noise;
        private final double xzScale;
        private final double yScale;

        public CustomNoiseFunction(NormalNoise noise, double xzScale, double yScale) {
            this.noise = noise;
            this.xzScale = xzScale;
            this.yScale = yScale;
        }

        @Override
        public double compute(FunctionContext context) {
            double x = context.blockX() * xzScale;
            double y = context.blockY() * yScale;
            double z = context.blockZ() * xzScale;
            return noise.getValue(x, y, z);
        }

        @Override
        public double minValue() {
            return -noise.maxValue(); // Noise typically ranges [-max, max]
        }

        @Override
        public double maxValue() {
            return noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            // Simplified codec; in a full implementation, you'd serialize parameters
            return KeyDispatchDataCodec.of(MapCodec.unit(this));
        }
    }


    class RadialGradient implements DensityFunction.SimpleFunction {
        private final int centerX, centerY, centerZ;
        private final double radius;
        private final double fromValue, toValue;

        public RadialGradient(TripleInt center, double radius, double fromValue, double toValue) {
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.radius = radius;
            this.fromValue = fromValue;
            this.toValue = toValue;
        }

        @Override
        public double compute(FunctionContext context) {
            double dx = context.blockX() - centerX;
            double dy = context.blockY() - centerY;
            double dz = context.blockZ() - centerZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double t = Math.min(distance / radius, 1.0);
            double smoothT = 1.0 - t * t; // Quadratic for smoothness
            return Mth.lerp(smoothT, fromValue, toValue);
        }

        @Override
        public double minValue() { return Math.min(fromValue, toValue); }
        @Override
        public double maxValue() { return Math.max(fromValue, toValue); }
        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return KeyDispatchDataCodec.of(MapCodec.unit(this));
        }
    }
    //END RADIAL GRADIENT


}
//END CLASS

