/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.util.ArrayDeque
import java.util.UUID

/**
 * Handles chunk forceloading for cameras.
 * Chunks are queued and processed in small batches each tick to avoid overwhelming the server.
 */
object CameraChunkLoader {
    private const val MAX_CHUNKS_PER_TICK = 16

    private data class CameraState(
        var dimension: ResourceKey<Level>,
        val chunks: MutableSet<Long>
    )

    private data class ChunkRequest(
        val dimension: ResourceKey<Level>,
        val chunkPos: ChunkPos,
        val load: Boolean
    )

    private val cameraStates = mutableMapOf<UUID, CameraState>()
    private val chunkOwners = mutableMapOf<ResourceKey<Level>, MutableMap<Long, MutableSet<UUID>>>()
    private val pendingRequests: ArrayDeque<ChunkRequest> = ArrayDeque()
    private val pendingAsyncLoads = mutableSetOf<Pair<ResourceKey<Level>, Long>>()

    fun ensureArea(level: ServerLevel, cameraId: UUID, centerChunk: ChunkPos, radius: Int) {
        val dimension = level.dimension()
        val state = cameraStates[cameraId]?.takeIf { it.dimension == dimension } ?: run {
            cameraStates.remove(cameraId)?.let { oldState ->
                oldState.chunks.forEach { chunkLong ->
                    removeOwnership(oldState.dimension, chunkLong, cameraId)
                }
            }

            CameraState(dimension, mutableSetOf()).also { cameraStates[cameraId] = it }
        }

        val newChunks = buildChunkSet(centerChunk, radius)
        val currentChunks = state.chunks

        val toRemove = currentChunks.minus(newChunks)
        val toAdd = newChunks.minus(currentChunks)

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { chunkLong ->
                removeOwnership(dimension, chunkLong, cameraId)
                currentChunks.remove(chunkLong)
            }
        }

        if (toAdd.isNotEmpty()) {
            toAdd.forEach { chunkLong ->
                addOwnership(dimension, chunkLong, cameraId)
                currentChunks.add(chunkLong)
            }
        }
    }

    fun clear(cameraId: UUID) {
        val state = cameraStates.remove(cameraId) ?: return
        val dimension = state.dimension
        state.chunks.forEach { chunkLong ->
            removeOwnership(dimension, chunkLong, cameraId)
        }
    }

    fun tick(server: MinecraftServer) {
        if (pendingRequests.isEmpty()) {
            return
        }

        var processed = 0
        while (processed < MAX_CHUNKS_PER_TICK && pendingRequests.isNotEmpty()) {
            val request = pendingRequests.removeFirst()
            val level = server.getLevel(request.dimension) ?: continue

            level.setChunkForced(request.chunkPos.x, request.chunkPos.z, request.load)

            if (request.load) {
                scheduleAsyncLoad(level, request.dimension, request.chunkPos)
            }

            processed++
        }
    }

    private fun addOwnership(dimension: ResourceKey<Level>, chunkLong: Long, cameraId: UUID) {
        val ownersForDimension = chunkOwners.computeIfAbsent(dimension) { mutableMapOf() }
        val owners = ownersForDimension.computeIfAbsent(chunkLong) { mutableSetOf() }

        if (owners.add(cameraId) && owners.size == 1) {
            pendingRequests.add(
                ChunkRequest(
                    dimension,
                    ChunkPos(chunkLong),
                    true
                )
            )
        }
    }

    private fun removeOwnership(dimension: ResourceKey<Level>, chunkLong: Long, cameraId: UUID) {
        val ownersForDimension = chunkOwners[dimension] ?: return
        val owners = ownersForDimension[chunkLong] ?: return

        if (!owners.remove(cameraId)) {
            return
        }

        if (owners.isEmpty()) {
            ownersForDimension.remove(chunkLong)
            if (ownersForDimension.isEmpty()) {
                chunkOwners.remove(dimension)
            }

            pendingRequests.add(
                ChunkRequest(
                    dimension,
                    ChunkPos(chunkLong),
                    false
                )
            )
        }
    }

    private fun scheduleAsyncLoad(level: ServerLevel, dimension: ResourceKey<Level>, chunkPos: ChunkPos) {
        val key = dimension to chunkPos.toLong()

        if (!pendingAsyncLoads.add(key)) {
            return
        }

        if (Cobblemon.LOGGER.isDebugEnabled) {
            Cobblemon.LOGGER.debug(
                "[CobblemonCamera] Scheduling async load for chunk {} in {}",
                chunkPos,
                dimension.location()
            )
        }

        level.chunkSource
            .getChunkFuture(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
            .thenAcceptAsync({ result ->
                result.ifSuccess { chunkAccess ->
                    if (chunkAccess is LevelChunk && Cobblemon.LOGGER.isDebugEnabled) {
                        Cobblemon.LOGGER.debug(
                            "[CobblemonCamera] Async loaded chunk {} (light={}, entities={})",
                            chunkPos,
                            chunkAccess.isLightCorrect,
                            chunkAccess.blockEntities.size
                        )
                    }
                }

                pendingAsyncLoads.remove(key)
            }, level.server)
            .exceptionally { throwable ->
                level.server.execute {
                    pendingAsyncLoads.remove(key)
                    Cobblemon.LOGGER.warn(
                        "[CobblemonCamera] Exception while async loading chunk {}: {}",
                        chunkPos,
                        throwable.message
                    )
                }
                null
            }
    }

    private fun buildChunkSet(centerChunk: ChunkPos, radius: Int): MutableSet<Long> {
        val chunks = mutableSetOf<Long>()

        for (x in centerChunk.x - radius..centerChunk.x + radius) {
            for (z in centerChunk.z - radius..centerChunk.z + radius) {
                chunks.add(ChunkPos.asLong(x, z))
            }
        }

        return chunks
    }
}
