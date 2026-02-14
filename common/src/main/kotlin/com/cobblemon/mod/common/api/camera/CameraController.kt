/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.integration.server.CameraExecutor
import com.cobblemon.mod.common.api.camera.integration.server.CameraInstance
import com.cobblemon.mod.common.api.camera.integration.server.CameraNetworkSync
import com.cobblemon.mod.common.api.camera.registry.TimelineRegistry
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Server-side camera controller.
 *
 * Orchestrates camera instance, executor, network sync, and chunk/viewer management.
 * Delegates responsibilities to focused components.
 */
class CameraController(
    override val id: UUID = UUID.randomUUID(),
    val context: Map<String, MoValue> = emptyMap(),
    override val origin: Vec3 = Vec3.ZERO,
    val queryFunctions: Map<String, java.util.function.Function<MoParams, Any>> = emptyMap()
) : ICameraController {
    private val instance: CameraInstance
    private val runtime: MoLangRuntime
    private lateinit var executor: CameraExecutor
    private lateinit var networkSync: CameraNetworkSync

    private val viewers = mutableSetOf<UUID>()
    private val viewerChunkStates = mutableMapOf<UUID, ViewerChunkState>()
    private var level: ServerLevel? = null

    init {
        runtime = MoLangRuntime().setup()
        runtime.environment.query.functions.putAll(queryFunctions)
        instance = CameraInstance(id, origin, context, queryFunctions)
        executor = CameraExecutor(instance, runtime, context)
        networkSync = CameraNetworkSync(id, viewers)
        Cobblemon.LOGGER.debug("[CameraController] Initialized $id")
    }

    private data class ViewerChunkState(
        val viewDistance: Int,
        var centerChunk: ChunkPos,
        var chunkView: ChunkTrackingView
    )

    override fun startTimeline(timelineId: String): Boolean {
        val timeline = TimelineRegistry.getTimeline(timelineId)
        if (timeline == null) {
            Cobblemon.LOGGER.error("[CameraController] Timeline '$timelineId' not found")
            return false
        }

        return if (instance.startTimeline(timeline)) {
            Cobblemon.LOGGER.info("[CameraController] Started '$timelineId'")
            true
        } else {
            Cobblemon.LOGGER.error("[CameraController] Engine start failed for '$timelineId'")
            false
        }
    }

    override fun tick() {
        val currentLevel = level ?: return
        val stillActive = executor.tick(currentLevel, networkSync) { chunkPos ->
            updateViewerChunkViews(chunkPos)
        }

        // If timeline ended, stop the camera and notify all viewers
        if (!stillActive && viewers.isNotEmpty()) {
            Cobblemon.LOGGER.info("[CameraController] Timeline ended naturally, stopping camera")
            stop()
        }
    }

    private fun updateViewerChunkViews(chunkPos: ChunkPos) {
        viewerChunkStates.forEach { (viewerId, state) ->
            if (state.centerChunk == chunkPos) return@forEach

            state.centerChunk = chunkPos
            val newView = ChunkTrackingView.of(chunkPos, state.viewDistance)
            state.chunkView = newView
            CameraManager.registerViewerChunkView(viewerId, newView)
        }

        val serverLevel = level
        if (serverLevel != null && viewerChunkStates.isNotEmpty()) {
            val maxDistance = viewerChunkStates.values.maxOf { it.viewDistance }
            CameraChunkLoader.ensureArea(serverLevel, id, chunkPos, maxDistance)
        }
    }

    override fun addViewer(player: ServerPlayer) {
        viewers.add(player.uuid)
        registerViewerChunkView(player, origin)
        networkSync.sendStart(origin)
    }

    private fun registerViewerChunkView(player: ServerPlayer, centerPosition: Vec3) {
        val playerLevel = player.serverLevel()
        if (level == null || level != playerLevel) {
            level?.let { CameraChunkLoader.clear(id) }
            level = playerLevel
        }

        val maxViewDistance = player.server.playerList.viewDistance
        val viewDistance = Mth.clamp(player.requestedViewDistance(), 2, maxViewDistance)
        val chunkPos = chunkPosFrom(centerPosition)
        val chunkView = ChunkTrackingView.of(chunkPos, viewDistance)

        viewerChunkStates[player.uuid] = ViewerChunkState(viewDistance, chunkPos, chunkView)
        CameraManager.registerViewerChunkView(player.uuid, chunkView)

        val maxDistance = viewerChunkStates.values.maxOfOrNull { it.viewDistance } ?: viewDistance
        CameraChunkLoader.ensureArea(playerLevel, id, chunkPos, maxDistance)
    }

    override fun removeViewer(player: ServerPlayer) {
        // Send end packet to THIS player specifically
        networkSync.sendEndToPlayer(player)

        viewers.remove(player.uuid)
        viewerChunkStates.remove(player.uuid)
        CameraManager.clearViewerChunkView(player.uuid)

        if (viewers.isEmpty()) {
            CameraChunkLoader.clear(id)
            level = null
        } else {
            val serverLevel = level
            if (serverLevel != null && viewerChunkStates.isNotEmpty()) {
                val state = viewerChunkStates.values.first()
                val maxDistance = viewerChunkStates.values.maxOf { it.viewDistance }
                CameraChunkLoader.ensureArea(serverLevel, id, state.centerChunk, maxDistance)
            }
        }
    }

    override fun stop() {
        instance.stop()
        networkSync.sendEnd()
        viewerChunkStates.keys.toList().forEach { CameraManager.clearViewerChunkView(it) }
        viewerChunkStates.clear()
        CameraChunkLoader.clear(id)
        level = null
        viewers.clear()
    }

    override fun isActive() = instance.isActive()

    fun setFlag(name: String, value: Boolean) = setVariable(name, if (value) 1.0 else 0.0)

    override fun setVariable(name: String, value: Double) = instance.setVariable(name, value)

    override fun setFocusOverride(position: Vec3?) {
        instance.focusOverride = position
    }
    
    override fun triggerInstantTransition() = instance.triggerInstantTransition()

    private fun chunkPosFrom(position: Vec3): ChunkPos {
        val chunkX = SectionPos.blockToSectionCoord(Mth.floor(position.x))
        val chunkZ = SectionPos.blockToSectionCoord(Mth.floor(position.z))
        return ChunkPos(chunkX, chunkZ)
    }
}
