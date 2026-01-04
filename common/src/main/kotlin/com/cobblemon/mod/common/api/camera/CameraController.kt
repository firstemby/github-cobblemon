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
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.client.camera.*
import com.cobblemon.mod.common.net.messages.client.camera.CameraEndPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraStartPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraUpdatePacket
import com.cobblemon.mod.common.util.resolve
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID

class CameraController(
    override val id: UUID = UUID.randomUUID(),
    val context: Map<String, MoValue> = emptyMap(),
    override val origin: Vec3 = Vec3.ZERO,
    val queryFunctions: Map<String, java.util.function.Function<MoParams, Any>> = emptyMap()
) : ICameraController {
    companion object {
        private const val CHUNK_UPDATE_DISTANCE_BLOCKS = 16
        private const val CHUNK_UPDATE_DISTANCE_SQ = (CHUNK_UPDATE_DISTANCE_BLOCKS * CHUNK_UPDATE_DISTANCE_BLOCKS).toDouble()
    }

    private val runner: CameraTimelineRunner

    private var lastUpdateTime: Long = System.currentTimeMillis()

    private val viewers = mutableSetOf<UUID>()
    private val viewerChunkStates = mutableMapOf<UUID, ViewerChunkState>()
    private var level: ServerLevel? = null

    @Volatile
    private var focusOverride: Vec3? = null

    private var lastValidFocusPosition: Vec3? = null

    init {
        val runtime = MoLangRuntime().setup()
        runtime.environment.query.functions.putAll(queryFunctions)
        runner = CameraTimelineRunner(id, context, origin, runtime)
    }

    private data class ViewerChunkState(
        val viewDistance: Int,
        var centerChunk: ChunkPos,
        var chunkView: ChunkTrackingView
    )

    override fun startTimeline(timelineId: String): Boolean {
        return runner.startTimeline(timelineId)
    }

    override fun tick() {
        if (!runner.isActive) return

        val currentTime = System.currentTimeMillis()
        val deltaSeconds = (currentTime - lastUpdateTime) / 1000f
        lastUpdateTime = currentTime

        val transform = runner.tick(deltaSeconds)

        if (transform != null) {
            sendCameraUpdate(transform)
        } else {
            if (!runner.isActive) {
                stop()
            }
        }
    }

    private fun resolveFocusPosition(focus: CameraFocus): Vec3? {
        return when (focus) {
            is LookTarget.Entity -> {
                try {
                    val result = runner.runtime.resolve(focus.selector, context)
                    if (result is ObjectValue<*>) {
                        val entity = result.obj as? Entity

                        if (entity != null && !entity.isRemoved && entity.isAlive) {
                            val position = entity.position()
                            lastValidFocusPosition = position
                            position
                        } else {
                            val cached = lastValidFocusPosition
                            if (cached != null) {
                                Cobblemon.LOGGER.debug("[CobblemonCamera] Entity focus invalid, using last valid position")
                                cached
                            } else {
                                Cobblemon.LOGGER.warn("[CobblemonCamera] Entity focus invalid and no cached position, using origin")
                                origin
                            }
                        }
                    } else null
                } catch (e: Exception) {
                    Cobblemon.LOGGER.error("[CobblemonCamera] Failed to resolve entity focus", e)
                    lastValidFocusPosition
                }
            }
            is LookTarget.Point -> {
                val position = focus.position.toVec3(context, runtime = runner.runtime)
                lastValidFocusPosition = position
                position
            }
        }
    }

    private var lastCenterChunkUpdate: Vec3? = null

    private fun sendCameraUpdate(transform: CameraTransform) {
        val overrideFocus = focusOverride
        val focusPos = overrideFocus ?: transform.focus?.let { resolveFocusPosition(it) }

        val shouldUpdateCenter = lastCenterChunkUpdate?.let { last ->
            transform.position.distanceToSqr(last) > CHUNK_UPDATE_DISTANCE_SQ
        } ?: true

        if (shouldUpdateCenter) {
            updateViewerChunkViews(transform.position)
            lastCenterChunkUpdate = transform.position
        }

        val renderPlayerBody = runner.currentState?.renderPlayerBody ?: true

        val packet =
            CameraUpdatePacket(id, transform.position, focusPos, transform.manualRotation, shouldUpdateCenter, renderPlayerBody)
        val server = Cobblemon.implementation.server()
        viewers.mapNotNull { server?.playerList?.getPlayer(it) }.forEach { it.sendPacket(packet) }
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

    private fun updateViewerChunkViews(centerPosition: Vec3) {
        val chunkPos = chunkPosFrom(centerPosition)

        viewerChunkStates.forEach { (viewerId, state) ->
            if (state.centerChunk === chunkPos)
                return@forEach

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

    private fun chunkPosFrom(position: Vec3): ChunkPos {
        val chunkX = SectionPos.blockToSectionCoord(Mth.floor(position.x))
        val chunkZ = SectionPos.blockToSectionCoord(Mth.floor(position.z))

        return ChunkPos(chunkX, chunkZ)
    }

    override fun addViewer(player: ServerPlayer) {
        viewers.add(player.uuid)
        val startingPosition = origin

        registerViewerChunkView(player, startingPosition)
        player.sendPacket(CameraStartPacket(id, startingPosition))
    }

    override fun removeViewer(player: ServerPlayer) {
        viewers.remove(player.uuid)
        viewerChunkStates.remove(player.uuid)
        CameraManager.clearViewerChunkView(player.uuid)
        player.sendPacket(CameraEndPacket(id))

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
        runner.stop()

        val server = Cobblemon.implementation.server()
        viewers.mapNotNull { server?.playerList?.getPlayer(it) }.forEach {
            it.sendPacket(CameraEndPacket(id))
        }
        viewerChunkStates.keys.toList().forEach { CameraManager.clearViewerChunkView(it) }
        viewerChunkStates.clear()
        lastCenterChunkUpdate = null
        CameraChunkLoader.clear(id)
        level = null
        viewers.clear()
    }

    override fun isActive() = runner.isActive

    fun setFlag(name: String, value: Boolean) = setVariable(name, if (value) 1.0 else 0.0)

    override fun setVariable(name: String, value: Double) {
        runner.setVariable(name, value)
    }

    override fun setFocusOverride(position: Vec3?) {
        focusOverride = position
    }
}
