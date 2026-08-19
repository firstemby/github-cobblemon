/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.feature

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.mixin.accessor.ChunkGeneratorAccessor
import com.cobblemon.mod.common.world.placementmodifier.BeneathHeightmapPlacementModifier
import com.cobblemon.mod.common.world.placementmodifier.ConditionalCountPlacementModifier
import com.cobblemon.mod.common.world.placementmodifier.ConditionalRarityFilterPlacementModifier
import com.cobblemon.mod.common.world.placementmodifier.LocatePredicatePlacementModifier
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Stream
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.core.Registry
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.WorldgenRandom
import net.minecraft.world.level.levelgen.XoroshiroRandomSource
import net.minecraft.world.level.levelgen.placement.BiomeFilter
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter
import net.minecraft.world.level.levelgen.placement.CountPlacement
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement
import net.minecraft.world.level.levelgen.placement.InSquarePlacement
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.placement.PlacementContext
import net.minecraft.world.level.levelgen.placement.PlacementModifier
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement
import net.minecraft.world.level.levelgen.placement.RarityFilter
import net.minecraft.world.level.material.FluidState

/**
 * Locates placed features in unloaded and ungenerated terrain by replaying the decoration randomness the
 * chunk generator will use when (or did use when) the chunk decorates, the same way `/locate` predicts
 * structures entirely from seed math.
 *
 * For each candidate chunk this seeds a [WorldgenRandom] with the vanilla decoration seed
 * (`setDecorationSeed` -> `setFeatureSeed(step, index)`, with the feature's index taken from the
 * generator's `featuresPerStep` output) and runs the feature's real placement modifier chain against a
 * [SeedOnlyWorldGenLevel] that answers terrain queries from generator noise instead of chunks. RNG-driven
 * modifiers therefore replay bit-exactly. Terrain-driven ones (`environment_scan`,
 * `block_predicate_filter`) are answered from the generator's real noise column ([ChunkGenerator.getBaseColumn])
 * plus an approximation of surface rules (biome surface blocks, deepslate below y=0), so they are close but
 * not exact; a reported position is where the feature *attempts* to place and real generation can still
 * veto it.
 *
 * Candidate chunks that survive the seed model are then confirmed against the world via the
 * session-lifetime [CobblemonPlacedFeatureIndex], fed by `PlacedFeatureMixin`/`ChunkGeneratorMixin` as
 * chunks decorate. Chunks the index is authoritative for answer instantly; ungenerated chunks are generated
 * to [ChunkStatus.FEATURES] - full, real decoration - so their own placement writes the answer (the same
 * trick vanilla `/locate` uses at STRUCTURE_STARTS, taken to its conclusion, budgeted per search). Chunks
 * decorated outside the index's lifetime cannot be confirmed either way, so model-passing candidates there
 * are reported as predictions - exact for an unchanged world config, blind to interference from carvers,
 * structures, and earlier features.
 */
object PlacedFeatureLocator {

    /** How many candidate chunks one search may newly generate to FEATURES status for verification. */
    private const val GENERATION_BUDGET = 16

    /** How many chunk loads (including already-decorated chunks, which are cheaper) one search may make. */
    private const val LOAD_BUDGET = 128

    /** How many model-rejected candidates are kept as a fallback in case the seed-only model is wrong. */
    private const val DEFERRED_CANDIDATE_LIMIT = 64

    @Volatile
    private var locatableCache: Pair<Registry<PlacedFeature>, Map<PlacedFeature, String>>? = null

    /** The placed features `/locatefeature` supports: Cobblemon fossils, habitats, and ruins. */
    @JvmStatic
    fun isLocatable(id: ResourceLocation): Boolean =
        id.namespace == Cobblemon.MODID && (id.path.startsWith("fossils/") || id.path.startsWith("habitats/") || id.path.startsWith("ruins/"))

    /**
     * Called by `PlacedFeatureMixin` whenever a placed feature successfully generates, on whichever worker
     * thread is decorating. Locatable features are written to the dimension's session-lifetime
     * [CobblemonPlacedFeatureIndex] so searches can report them without re-deriving what a decorated chunk did.
     */
    @JvmStatic
    fun onFeaturePlaced(feature: PlacedFeature, context: PlacementContext, origin: BlockPos) {
        val level = context.level.level
        val featureId = locatableId(level, feature) ?: return
        val chunkKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(origin.x), SectionPos.blockToSectionCoord(origin.z))
        CobblemonPlacedFeatureIndex.get(level).add(featureId, chunkKey)
    }

    /**
     * Called by `ChunkGeneratorMixin` when a chunk finishes decorating. From this point the index is
     * authoritative for the chunk: a locatable feature not listed there verifiably did not generate in it.
     */
    @JvmStatic
    fun onChunkDecorated(level: ServerLevel, chunkPos: ChunkPos) {
        CobblemonPlacedFeatureIndex.get(level).markDecorated(chunkPos.toLong())
    }

    private fun locatableId(level: ServerLevel, feature: PlacedFeature): String? {
        val registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
        var cache = locatableCache
        if (cache == null || cache.first !== registry) {
            val map = java.util.IdentityHashMap<PlacedFeature, String>()
            for ((key, value) in registry.entrySet()) {
                if (isLocatable(key.location())) {
                    map[value] = key.location().toString()
                }
            }
            cache = registry to map
            locatableCache = cache
        }
        return cache.second[feature]
    }

    sealed interface LocateResult {
        /** The sites nearest to the search origin, closest first. Never empty. */
        class Found(val sites: List<Site>) : LocateResult
        object NotInDimension : LocateResult
        object NotFound : LocateResult
    }

    /**
     * A located site. [confirmed] sites are known placements - observed during real decoration. Unconfirmed
     * sites are full seed-model predictions in terrain that decorated before the placement index existed:
     * exact for the same world config it generated under, but blind to interference from carvers,
     * structures, and earlier features.
     */
    class Site(val pos: BlockPos, val confirmed: Boolean)

    /** How many nearest sites one search reports. */
    const val MAX_RESULTS = 5

    /**
     * Placement modifiers the replay understands. RNG draws happen inside the modifiers themselves, so any
     * modifier that only consumes randomness and [PlacementContext] height/block/biome queries is safe to
     * run against the seed-only level. Anything outside this set makes the feature unlocatable rather than
     * silently wrong.
     */
    private val REPLAYABLE_MODIFIERS = setOf<Class<out PlacementModifier>>(
        CountPlacement::class.java,
        RarityFilter::class.java,
        InSquarePlacement::class.java,
        HeightRangePlacement::class.java,
        HeightmapPlacement::class.java,
        EnvironmentScanPlacement::class.java,
        BlockPredicateFilter::class.java,
        BiomeFilter::class.java,
        RandomOffsetPlacement::class.java,
        BeneathHeightmapPlacementModifier::class.java,
        ConditionalCountPlacementModifier::class.java,
        ConditionalRarityFilterPlacementModifier::class.java,
        LocatePredicatePlacementModifier::class.java
    )

    /** The first modifier in the feature's chain that the replay cannot reproduce, or null if all are supported. */
    fun firstUnreplayableModifier(feature: PlacedFeature): PlacementModifier? =
        feature.placement().firstOrNull { modifier -> REPLAYABLE_MODIFIERS.none { it.isInstance(modifier) } }

    /**
     * Finds the nearest chunks within [radiusChunks] of [origin] where [target] verifiably generates,
     * nearest first, up to [MAX_RESULTS]. Y is not predicted; callers should present X/Z.
     */
    fun locate(
        level: ServerLevel,
        target: Holder<PlacedFeature>,
        origin: BlockPos,
        radiusChunks: Int
    ): LocateResult {
        val generator = level.chunkSource.generator
        val randomState = level.chunkSource.randomState()
        val feature = target.value()

        // The feature's decoration step and index within it, exactly as applyBiomeDecoration seeds them.
        var step = -1
        var index = -1
        val steps = (generator as ChunkGeneratorAccessor).featuresPerStep.get()
        for (k in steps.indices) {
            val i = steps[k].features().indexOf(feature)
            if (i >= 0) {
                step = k
                index = i
                break
            }
        }
        if (step < 0) {
            return LocateResult.NotInDimension
        }

        val stub = SeedOnlyWorldGenLevel(level, generator, randomState)
        val placementContext = PlacementContext(stub, generator, Optional.of(feature))
        val random = WorldgenRandom(XoroshiroRandomSource(0L))
        // A biome filter inside the chain already proves a biome at the position uses the feature, which
        // implies the chunk-neighbourhood membership check vanilla performs before attempting it.
        val needsMembershipCheck = feature.placement().none { it is BiomeFilter }
        val membershipCache = HashMap<Holder<Biome>, Boolean>()

        val originChunkX = SectionPos.blockToSectionCoord(origin.x)
        val originChunkZ = SectionPos.blockToSectionCoord(origin.z)
        // The nearest sites found so far, kept sorted nearest-first.
        val found = ArrayList<FoundSite>()

        locateInto(found, level, generator, randomState, placementContext, random, target, feature, step, index, originChunkX, originChunkZ, origin, radiusChunks, needsMembershipCheck, membershipCache)
        return if (found.isEmpty()) LocateResult.NotFound else LocateResult.Found(found.map { Site(it.pos, it.confirmed) })
    }

    private class FoundSite(val pos: BlockPos, val distSq: Double, val confirmed: Boolean)

    private fun featureIdOf(level: ServerLevel, feature: PlacedFeature): String? =
        level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(feature)?.toString()

    private fun locateInto(
        found: ArrayList<FoundSite>,
        level: ServerLevel,
        generator: ChunkGenerator,
        randomState: RandomState,
        placementContext: PlacementContext,
        random: WorldgenRandom,
        target: Holder<PlacedFeature>,
        feature: PlacedFeature,
        step: Int,
        index: Int,
        originChunkX: Int,
        originChunkZ: Int,
        origin: BlockPos,
        radiusChunks: Int,
        needsMembershipCheck: Boolean,
        membershipCache: MutableMap<Holder<Biome>, Boolean>
    ) {
        val placementIndex = CobblemonPlacedFeatureIndex.get(level)
        val featureId = featureIdOf(level, feature) ?: return
        val hasTerrainChecks = feature.placement().any { it is EnvironmentScanPlacement || it is BlockPredicateFilter }
        val budget = SearchBudget()
        // Candidates whose terrain scans fail against the seed-only noise model. The model reproduces real
        // worldgen closely (noise terrain, water, caves, approximated surface rules) but not perfectly, so
        // these are deferred rather than discarded and only probed if nothing model-approved confirms.
        val deferred = ArrayList<Triple<Int, Int, BlockPos>>()

        fun insertResult(pos: BlockPos, confirmed: Boolean) {
            val dx = (pos.x - origin.x).toDouble()
            val dz = (pos.z - origin.z).toDouble()
            val distSq = dx * dx + dz * dz
            val insertAt = found.indexOfFirst { distSq < it.distSq }.let { if (it < 0) found.size else it }
            if (insertAt < MAX_RESULTS) {
                found.add(insertAt, FoundSite(pos, distSq, confirmed))
                if (found.size > MAX_RESULTS) {
                    found.removeAt(found.size - 1)
                }
            }
        }

        for (ring in 0..radiusChunks) {
            // A hit in ring r is at least (r-1)*16 blocks away, so once that exceeds the farthest of a full
            // result list, no later ring can improve it.
            val closestPossible = ((ring - 1).coerceAtLeast(0) * 16).toDouble()
            if (found.size >= MAX_RESULTS && closestPossible * closestPossible > found.last().distSq) {
                break
            }
            forEachChunkInRing(originChunkX, originChunkZ, ring) { cx, cz ->
                // Stage 1: the chain with terrain scans assumed to pass. Exact for RNG and biome filters and
                // costs microseconds, so it cheaply eliminates the vast majority of chunks (failed rarity
                // roll, wrong biome) before any real chunk work happens.
                var pos = runChain(level, placementContext, random, feature, step, index, cx, cz, skipTerrainChecks = true)
                // Vanilla only attempts a feature when a biome in the surrounding 3x3 chunk neighbourhood
                // generates it. Runs after the cheaper RNG chain intentionally - the result is the same.
                if (pos != null && needsMembershipCheck && !neighbourhoodUsesFeature(level, generator, randomState, target, step, cx, cz, membershipCache)) {
                    pos = null
                }
                if (pos == null) {
                    return@forEachChunkInRing
                }
                // Stage 2: evaluate the terrain scans against the seed-only model of real worldgen - the
                // generator's actual noise columns (true terrain shape, water, noise caves) refined with an
                // approximation of surface rules - using the real placement modifier code throughout.
                // Model failures are deferred, not discarded: the model is close to real generation but
                // cannot know about carvers, structures, or earlier features.
                val chunkKey = ChunkPos.asLong(cx, cz)
                if (hasTerrainChecks && !placementIndex.contains(featureId, chunkKey) &&
                    runChain(level, placementContext, random, feature, step, index, cx, cz, skipTerrainChecks = false) == null
                ) {
                    if (deferred.size < DEFERRED_CANDIDATE_LIMIT) {
                        deferred.add(Triple(cx, cz, pos))
                    }
                    return@forEachChunkInRing
                }
                // Stage 3: confirm against the world - the index answers instantly and authoritatively for
                // every chunk decorated while it was active; otherwise the chunk generates now and the
                // index answers from its real decoration. Terrain that predates the index can never be
                // confirmed, but the full seed model already passed here, so it is reported as a
                // prediction - exact for an unchanged world config, blind to decoration-order interference.
                when (confirm(level, placementIndex, featureId, cx, cz, budget)) {
                    Confirmation.CONFIRMED -> insertResult(pos, confirmed = true)
                    Confirmation.UNKNOWABLE -> insertResult(pos, confirmed = false)
                    Confirmation.ABSENT, Confirmation.EXHAUSTED -> Unit
                }
            }
        }

        // The seed-only model rejected every candidate the budgets could have confirmed. The model can be
        // wrong (a scan target it cannot produce, terrain altered by carvers/structures), so probe the
        // nearest deferred candidates rather than go blind. Only real confirmations count here - for these
        // the model already said no, so an unknowable chunk stays unreported.
        if (found.isEmpty()) {
            for ((cx, cz, pos) in deferred) {
                if (confirm(level, placementIndex, featureId, cx, cz, budget) == Confirmation.CONFIRMED) {
                    insertResult(pos, confirmed = true)
                }
                if (found.size >= MAX_RESULTS) {
                    break
                }
            }
        }
    }

    private enum class Confirmation {
        /** The feature verifiably generated in the chunk. */
        CONFIRMED,

        /** The chunk decorated under the index and the feature verifiably did not generate. */
        ABSENT,

        /** The chunk decorated before the index existed; only the seed model can speak for it. */
        UNKNOWABLE,

        /** The budgets ran out before an answer existed. */
        EXHAUSTED
    }

    /**
     * Whether [featureId] generated in chunk ([chunkX], [chunkZ]): instantly from the index when it is
     * authoritative for the chunk, else by generating the chunk (budgeted) and letting its real decoration
     * write the answer.
     */
    private fun confirm(
        level: ServerLevel,
        placementIndex: CobblemonPlacedFeatureIndex,
        featureId: String,
        chunkX: Int,
        chunkZ: Int,
        budget: SearchBudget
    ): Confirmation {
        val chunkKey = ChunkPos.asLong(chunkX, chunkZ)
        if (placementIndex.contains(featureId, chunkKey)) {
            return Confirmation.CONFIRMED
        }
        if (placementIndex.isDecorated(chunkKey)) {
            return Confirmation.ABSENT
        }
        if (placementIndex.isPreIndex(chunkKey)) {
            return Confirmation.UNKNOWABLE
        }
        if (budget.loads <= 0 || budget.generations <= 0) {
            return Confirmation.EXHAUSTED
        }
        budget.loads--
        level.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true)
        return when {
            placementIndex.contains(featureId, chunkKey) -> {
                budget.generations--
                Confirmation.CONFIRMED
            }
            placementIndex.isDecorated(chunkKey) -> {
                budget.generations--
                Confirmation.ABSENT
            }
            else -> {
                // Loaded but no decoration ran: this chunk predates the index. Remembered so no future
                // search wastes a load on it.
                placementIndex.markPreIndex(chunkKey)
                Confirmation.UNKNOWABLE
            }
        }
    }

    private class SearchBudget {
        var loads = LOAD_BUDGET
        var generations = GENERATION_BUDGET
    }

    private fun runChain(
        level: ServerLevel,
        placementContext: PlacementContext,
        random: WorldgenRandom,
        feature: PlacedFeature,
        step: Int,
        index: Int,
        chunkX: Int,
        chunkZ: Int,
        skipTerrainChecks: Boolean
    ): BlockPos? {
        val minBlockX = SectionPos.sectionToBlockCoord(chunkX)
        val minBlockZ = SectionPos.sectionToBlockCoord(chunkZ)
        val decorationSeed = random.setDecorationSeed(level.seed, minBlockX, minBlockZ)
        random.setFeatureSeed(decorationSeed, index, step)

        var stream = Stream.of(BlockPos(minBlockX, level.minBuildHeight, minBlockZ))
        for (modifier in feature.placement()) {
            // Terrain scans consume no randomness, so skipping them keeps the RNG stream aligned.
            if (skipTerrainChecks && (modifier is EnvironmentScanPlacement || modifier is BlockPredicateFilter)) {
                continue
            }
            stream = stream.flatMap { modifier.getPositions(placementContext, random, it) }
        }
        // Lazy streams draw RNG in the same order vanilla does, and the first position's draws all happen
        // before any later position's, so the first attempted position is exact.
        return stream.findFirst().orElse(null)
    }

    /**
     * Approximates vanilla's biome membership gate by noise-sampling quart biomes across the 3x3 chunk
     * neighbourhood at several heights. Sparse sampling can miss thin biome slivers right at borders.
     */
    private fun neighbourhoodUsesFeature(
        level: ServerLevel,
        generator: ChunkGenerator,
        randomState: RandomState,
        target: Holder<PlacedFeature>,
        step: Int,
        chunkX: Int,
        chunkZ: Int,
        cache: MutableMap<Holder<Biome>, Boolean>
    ): Boolean {
        val sampler = randomState.sampler()
        val yQuarts = intArrayOf(
            QuartPos.fromBlock(level.minBuildHeight + 16),
            QuartPos.fromBlock(0),
            QuartPos.fromBlock(generator.seaLevel + 8),
            QuartPos.fromBlock(generator.seaLevel + 48)
        )
        for (cx in chunkX - 1..chunkX + 1) {
            for (cz in chunkZ - 1..chunkZ + 1) {
                val baseQuartX = QuartPos.fromSection(cx)
                val baseQuartZ = QuartPos.fromSection(cz)
                for (dqx in 0..3 step 2) {
                    for (dqz in 0..3 step 2) {
                        for (quartY in yQuarts) {
                            val biome = generator.biomeSource.getNoiseBiome(baseQuartX + dqx, quartY, baseQuartZ + dqz, sampler)
                            val uses = cache.getOrPut(biome) {
                                val features = generator.getBiomeGenerationSettings(biome).features()
                                step < features.size && features[step].contains(target)
                            }
                            if (uses) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private inline fun forEachChunkInRing(centerX: Int, centerZ: Int, ring: Int, action: (Int, Int) -> Unit) {
        if (ring == 0) {
            action(centerX, centerZ)
            return
        }
        for (dx in -ring..ring) {
            action(centerX + dx, centerZ - ring)
            action(centerX + dx, centerZ + ring)
        }
        for (dz in -ring + 1..ring - 1) {
            action(centerX - ring, centerZ + dz)
            action(centerX + ring, centerZ + dz)
        }
    }

    /**
     * A [WorldGenLevel] that answers every terrain question from generator noise so that placement
     * modifiers can run without any chunk existing. Heights come from [ChunkGenerator.getBaseHeight],
     * biomes from noise sampling, and block states from [ChunkGenerator.getBaseColumn] - the real noise
     * terrain, including noise caves and open water - refined with an approximation of surface rules:
     * a biome-appropriate surface block on top, a few subsurface layers, and deepslate below y=0. Carvers
     * and decorated blocks are unknowable here, which is why results are "attempted" sites. Anything that
     * would touch a real chunk throws.
     */
    private class SeedOnlyWorldGenLevel(
        private val real: ServerLevel,
        private val generator: ChunkGenerator,
        private val randomState: RandomState
    ) : WorldGenLevel by real {
        private val columns = HashMap<Long, NoiseColumn>()
        private val topSolidYs = HashMap<Long, Int>()

        private fun column(x: Int, z: Int): NoiseColumn = columns.getOrPut(ChunkPos.asLong(x, z)) {
            generator.getBaseColumn(x, z, real, randomState)
        }

        /** Y of the highest solid block, from the same noise the column uses. */
        private fun topSolidY(x: Int, z: Int): Int = topSolidYs.getOrPut(ChunkPos.asLong(x, z)) {
            generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, real, randomState) - 1
        }

        override fun getHeight(type: Heightmap.Types, x: Int, z: Int): Int =
            generator.getBaseHeight(x, z, type, real, randomState)

        override fun getBlockState(pos: BlockPos): BlockState {
            if (pos.y < real.minBuildHeight || pos.y >= real.maxBuildHeight) {
                return Blocks.AIR.defaultBlockState()
            }
            val state = column(pos.x, pos.z).getBlock(pos.y)
            if (state.isAir || !state.fluidState.isEmpty) {
                return state
            }
            val top = topSolidY(pos.x, pos.z)
            return when {
                pos.y == top -> surfaceBlock(pos, submerged = !column(pos.x, pos.z).getBlock(top + 1).fluidState.isEmpty)
                pos.y > top - 4 && pos.y < top -> subsurfaceBlock(pos, top, state)
                // Sandy surfaces sit on a band of sandstone, like the real surface rules produce.
                pos.y > top - 9 && pos.y < top -> when {
                    topBlock(pos, top).`is`(Blocks.SAND) -> Blocks.SANDSTONE.defaultBlockState()
                    topBlock(pos, top).`is`(Blocks.RED_SAND) -> Blocks.RED_SANDSTONE.defaultBlockState()
                    else -> state
                }
                state.`is`(Blocks.STONE) && pos.y < 0 -> Blocks.DEEPSLATE.defaultBlockState()
                else -> state
            }
        }

        private fun topBlock(pos: BlockPos, top: Int): BlockState =
            surfaceBlock(BlockPos(pos.x, top, pos.z), submerged = !column(pos.x, pos.z).getBlock(top + 1).fluidState.isEmpty)

        /** Rough stand-in for surface rules; just enough for environment scans hunting surface materials. */
        private fun surfaceBlock(pos: BlockPos, submerged: Boolean): BlockState {
            val biome = getBiome(pos)
            return when {
                biome.`is`(Biomes.DESERT) || biome.`is`(BiomeTags.IS_BEACH) -> Blocks.SAND.defaultBlockState()
                biome.`is`(BiomeTags.IS_BADLANDS) -> Blocks.RED_SAND.defaultBlockState()
                biome.`is`(Biomes.STONY_SHORE) || biome.`is`(Biomes.STONY_PEAKS) -> Blocks.STONE.defaultBlockState()
                biome.`is`(Biomes.MUSHROOM_FIELDS) -> Blocks.MYCELIUM.defaultBlockState()
                submerged -> if (biome.`is`(BiomeTags.IS_RIVER)) Blocks.GRAVEL.defaultBlockState() else Blocks.SAND.defaultBlockState()
                else -> Blocks.GRASS_BLOCK.defaultBlockState()
            }
        }

        private fun subsurfaceBlock(pos: BlockPos, top: Int, state: BlockState): BlockState {
            val surface = surfaceBlock(BlockPos(pos.x, top, pos.z), submerged = !column(pos.x, pos.z).getBlock(top + 1).fluidState.isEmpty)
            return when {
                surface.`is`(Blocks.GRASS_BLOCK) || surface.`is`(Blocks.MYCELIUM) -> Blocks.DIRT.defaultBlockState()
                surface.`is`(Blocks.SAND) -> Blocks.SAND.defaultBlockState()
                surface.`is`(Blocks.RED_SAND) -> Blocks.RED_SAND.defaultBlockState()
                else -> state
            }
        }

        override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

        override fun isStateAtPosition(pos: BlockPos, predicate: Predicate<BlockState>): Boolean = predicate.test(getBlockState(pos))

        override fun isFluidAtPosition(pos: BlockPos, predicate: Predicate<FluidState>): Boolean = predicate.test(getFluidState(pos))

        override fun getBiome(pos: BlockPos): Holder<Biome> =
            generator.biomeSource.getNoiseBiome(QuartPos.fromBlock(pos.x), QuartPos.fromBlock(pos.y), QuartPos.fromBlock(pos.z), randomState.sampler())

        override fun getUncachedNoiseBiome(x: Int, y: Int, z: Int): Holder<Biome> =
            generator.biomeSource.getNoiseBiome(x, y, z, randomState.sampler())

        override fun getChunk(x: Int, z: Int): ChunkAccess = throw chunkAccess()

        override fun getChunk(x: Int, z: Int, requiredStatus: ChunkStatus): ChunkAccess = throw chunkAccess()

        override fun getChunk(x: Int, z: Int, requiredStatus: ChunkStatus, nonnull: Boolean): ChunkAccess? = throw chunkAccess()

        override fun setBlock(pos: BlockPos, state: BlockState, flags: Int): Boolean = throw chunkAccess()

        override fun setBlock(pos: BlockPos, state: BlockState, flags: Int, recursionLeft: Int): Boolean = throw chunkAccess()

        private fun chunkAccess() = UnsupportedOperationException("Placed feature location is seed-only and must not touch chunks")
    }
}