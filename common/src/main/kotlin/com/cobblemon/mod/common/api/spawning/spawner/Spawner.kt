/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.spawner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnBucketChosenEvent
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.SpawningZoneGenerator
import com.cobblemon.mod.common.api.spawning.detail.EntitySpawnResult
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePositionResolver
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.prioritizedAreaCalculators
import com.cobblemon.mod.common.api.spawning.selection.SpawningSelector
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.isBoxLoaded
import com.cobblemon.mod.common.util.removeIf
import com.cobblemon.mod.common.util.squeezeWithinBounds
import com.cobblemon.mod.common.util.toBlockPos
import com.cobblemon.mod.common.util.toVec3f
import com.cobblemon.mod.common.util.weightedSelection
import kotlin.math.max
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Interface representing something that performs the action of spawning. Various functions
 * exist to streamline the process of using the [BestSpawner].
 *
 * The simplest implementation is a [BasicSpawner]
 *
 * @author Hiroku
 * @since January 24th, 2022
 */
interface Spawner {
    val name: String
    val influences: MutableList<SpawningInfluence>
    val buckets: Map<String, Float>
    var spawnPool: SpawnPool
    var selector: SpawningSelector<*>
    var generator: SpawningZoneGenerator
    var resolver: AreaSpawnablePositionResolver
    var maxPokemonPerChunk: Float

    fun <R> afterSpawn(action: SpawnAction<R>, result: R) {}

    companion object {
        /** The chunk range radius over which spawning will roughly keep the entity density under control. */
        const val ENTITY_LIMIT_CHUNK_RANGE = 3
    }

    fun getMatchingSpawns(bucket: String, spawnablePosition: SpawnablePosition): List<SpawnDetail> {
        val spawns = mutableListOf<SpawnDetail>()
        spawns.addAll(spawnPool.retrieve(bucket, spawnablePosition).filter { it.isSatisfiedBy(spawnablePosition) })
        spawnablePosition.influences.forEach { influence ->
            val influencedSpawns = influence.injectSpawns(bucket, spawnablePosition)
            if (influencedSpawns != null) {
                spawns.addAll(influencedSpawns)
            }
        }
        return spawns
    }

    /**
     * Runs the spawner for a specific [SpawnablePosition] and calculates a [SpawnAction] that could spawn there.
     * This does not trigger the resulting spawn. You can trigger the spawn in the same action by running
     * [runForPosition] instead or manually complete the [SpawnAction] it returns using [SpawnAction.complete].
     */
    fun calculateSpawnActionForPosition(
        cause: SpawnCause,
        spawnablePosition: SpawnablePosition,
    ): SpawnAction<*>? {
        influences.removeIf { it.isExpired() }
        spawnablePosition.influences.addAll(influences)
        return selector.select(
            spawner = this,
            bucketSelector = { chooseBucket(cause, spawnablePosition.influences) },
            spawnablePositions = listOf(spawnablePosition),
            maxSpawns = 1
        ).firstOrNull()
    }

    fun calculateSpawnActionsForArea(
        zoneInput: SpawningZoneInput,
        maxSpawns: Int?
    ): List<SpawnAction<*>> {
        val maxSpawns = maxSpawns ?: Cobblemon.config.maximumSpawnsPerPass
        influences.removeIf { it.isExpired() }

        val constrainedArea = constrainArea(zoneInput)
            ?: return emptyList()

        val areaBox = AABB.ofSize(
            Vec3(constrainedArea.getCenter().toVec3f()),
            ENTITY_LIMIT_CHUNK_RANGE * 16.0,
            1000.0,
            ENTITY_LIMIT_CHUNK_RANGE * 16.0
        )

        if (!constrainedArea.world.isBoxLoaded(areaBox)) {
            return emptyList()
        }

        val numberNearby = constrainedArea.world.getEntitiesOfClass(
            PokemonEntity::class.java,
            areaBox,
            PokemonEntity::countsTowardsSpawnCap
        ).size

        val chunksCovered = ENTITY_LIMIT_CHUNK_RANGE * ENTITY_LIMIT_CHUNK_RANGE
        val maxPokemonPerChunk = max(Cobblemon.config.pokemonPerChunk, zoneInput.cause.spawner.maxPokemonPerChunk)
        if (numberNearby.toFloat() / chunksCovered >= maxPokemonPerChunk) {
            return emptyList()
        }

        val zone = generator.generate(this, constrainedArea)
        val spawnablePositions = resolver.resolve(this, prioritizedAreaCalculators, zone)
        val influences = influences + zone.unconditionalInfluences

        return selector.select(
            spawner = this,
            bucketSelector = { chooseBucket(zoneInput.cause, influences) },
            spawnablePositions = spawnablePositions,
            maxSpawns = maxSpawns
        )
    }

    /**
     * Runs the spawner at the given [SpawnablePosition] and returns whatever the calculated [SpawnAction] produces,
     * if any spawn occurred. For most implementations of [SpawnAction], the resulting type is
     * [EntitySpawnResult]. Technically you can make spawns for anything though.
     */
    fun runForPosition(
        cause: SpawnCause,
        spawnablePosition: SpawnablePosition
    ): Any? {
        val action = calculateSpawnActionForPosition(
            cause = cause,
            spawnablePosition = spawnablePosition
        ) ?: return null

        return action.complete()
    }

    fun runForArea(
        zoneInput: SpawningZoneInput,
        maxSpawns: Int? = null
    ): List<Any> {
        val spawnActions = calculateSpawnActionsForArea(zoneInput = zoneInput, maxSpawns = maxSpawns)
        val results = mutableListOf<Any>()
        for (spawnAction in spawnActions) {
            spawnAction.complete()?.let(results::add)
        }
        return results
    }

    fun copyInfluences() = influences.filter { !it.isExpired() }.toMutableList()

    fun chooseBucket(cause: SpawnCause, influences: List<SpawningInfluence>): String {
        val bucketWeights = buckets.toMutableMap()
        influences.forEach { it.affectBucketWeights(bucketWeights) }
        val usedBuckets = spawnPool.buckets.intersect(bucketWeights.keys)
        bucketWeights.removeIf { it.key !in usedBuckets }
        val bucket = bucketWeights.entries.weightedSelection { it.value }?.key ?: buckets.keys.first()
        val event = SpawnBucketChosenEvent(
            spawner = this,
            spawnCause = cause,
            bucket = bucket,
            bucketWeights = bucketWeights
        )
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.post(event)
        return event.bucket
    }

    fun isValidStartPoint(world: Level, chunk: ChunkAccess, startPos: BlockPos.MutableBlockPos): Boolean {
        val abovePos = startPos.above()
        if (!world.isLoaded(startPos) || !world.isLoaded(abovePos)) {
            return false
        }
        val mid = chunk.getBlockState(startPos)
        val above = chunk.getBlockState(abovePos)

        return above.isPathfindable(PathComputationType.AIR) && mid.isAir
    }

    fun constrainArea(area: SpawningZoneInput): SpawningZoneInput? {
        val world = area.world
        val centerPos = area.getCenter().toBlockPos()

        val (centerChunkX, centerChunkZ) = Pair(SectionPos.blockToSectionCoord(centerPos.x), SectionPos.blockToSectionCoord(centerPos.z))
        // if the chunk isn't loaded, we don't want to go further & we don't want the getChunk function below to load/create the chunk.
        if (!world.areEntitiesLoaded(ChunkPos.asLong(centerChunkX, centerChunkZ))) return null

        val chunk = world.getChunk(centerChunkX, centerChunkZ, ChunkStatus.FULL) ?: return null

        val probePos = centerPos.mutable()
        var validY = if (isValidStartPoint(world, chunk, probePos)) centerPos.y else null

        if (validY == null) {
            for (offset in 1..Cobblemon.config.maxVerticalCorrectionBlocks) {
                if (isValidStartPoint(world, chunk, probePos.set(centerPos.x, centerPos.y + offset, centerPos.z))) {
                    validY = centerPos.y + offset
                    break
                } else if (isValidStartPoint(world, chunk, probePos.set(centerPos.x, centerPos.y - offset, centerPos.z))) {
                    validY = centerPos.y - offset
                    break
                }
            }
        }
        val targetY = validY ?: return null
        val newBaseY = (targetY - (area.height / 2)).coerceIn(world.minBuildHeight, world.maxBuildHeight)
        val minPos = world.squeezeWithinBounds(BlockPos(area.baseX, newBaseY, area.baseZ))
        val maxPos = world.squeezeWithinBounds(BlockPos(area.baseX + area.length, newBaseY + area.height, area.baseZ + area.width))

        val finalBox = AABB(
            minPos.x.toDouble(), minPos.y.toDouble(), minPos.z.toDouble(),
            maxPos.x.toDouble(), maxPos.y.toDouble(), maxPos.z.toDouble()
        )
        if (minPos.x < maxPos.x && minPos.y < maxPos.y && minPos.z < maxPos.z && world.isBoxLoaded(finalBox)) {
            return SpawningZoneInput(
                cause = area.cause,
                world = world,
                baseX = minPos.x,
                baseY = minPos.y,
                baseZ = minPos.z,
                length = maxPos.x - minPos.x,
                height = maxPos.y - minPos.y,
                width = maxPos.z - minPos.z
            )
        }

        return null
    }
}