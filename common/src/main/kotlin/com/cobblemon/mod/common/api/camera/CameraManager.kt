/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.components.CameraChunkManager
import com.cobblemon.mod.common.api.camera.components.CameraLifecycleManager
import com.cobblemon.mod.common.api.camera.components.CameraViewerManager
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.camera.CameraStartedEvent
import com.cobblemon.mod.common.api.events.camera.CameraStoppedEvent
import com.cobblemon.mod.common.api.events.camera.CameraViewerAddedEvent
import com.cobblemon.mod.common.api.events.camera.CameraViewerRemovedEvent
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.function.Function

object CameraManager : ICameraManager {

    private val lifecycleManager = CameraLifecycleManager()
    private val viewerManager = CameraViewerManager()
    private val chunkManager = CameraChunkManager()

    override fun startCamera(
        timelineId: String,
        context: Map<String, MoValue>,
        viewers: List<ServerPlayer>,
        origin: Vec3,
        queryFunctions: Map<String, Function<MoParams, Any>>
    ): UUID? {
        val camera = CameraController(
            context = context,
            origin = origin,
            queryFunctions = queryFunctions
        )

        val preEvent = CameraStartedEvent.Pre(camera.id, camera, timelineId, viewers)
        CobblemonEvents.CAMERA_STARTED_PRE.emit(preEvent)
        if (preEvent.isCanceled) {
            Cobblemon.LOGGER.info("[CobblemonCamera] Camera start canceled by event listener")
            return null
        }

        if (!camera.startTimeline(timelineId)) {
            Cobblemon.LOGGER.warn("[CobblemonCamera] Failed to start timeline '$timelineId'")
            return null
        }

        lifecycleManager.registerCamera(camera)

        viewers.forEach { player ->
            camera.addViewer(player)
            viewerManager.assignViewerToCamera(player.uuid, camera.id)

            CobblemonEvents.CAMERA_VIEWER_ADDED.emit(
                CameraViewerAddedEvent(camera.id, camera, player)
            )
        }

        CobblemonEvents.CAMERA_STARTED_POST.emit(
            CameraStartedEvent.Post(camera.id, camera, timelineId, viewers)
        )

        Cobblemon.LOGGER.info("[CobblemonCamera] Started camera ${camera.id} with timeline '$timelineId' for ${viewers.size} viewers")
        return camera.id
    }

    override fun stopCamera(cameraId: UUID) {
        lifecycleManager.getCamera(cameraId)?.let { camera ->
            CobblemonEvents.CAMERA_STOPPED_PRE.emit(
                CameraStoppedEvent.Pre(cameraId, camera, CameraStoppedEvent.StopReason.MANUAL)
            )

            camera.stop()
            lifecycleManager.unregisterCamera(cameraId)
            viewerManager.removeViewersForCamera(cameraId)

            CobblemonEvents.CAMERA_STOPPED_POST.emit(
                CameraStoppedEvent.Post(cameraId, camera, CameraStoppedEvent.StopReason.MANUAL)
            )

            Cobblemon.LOGGER.info("[CobblemonCamera] Stopped camera $cameraId")
        }
    }

    override fun stopCameraForPlayer(player: ServerPlayer) {
        viewerManager.getCameraForPlayer(player.uuid)?.let { cameraId ->
            lifecycleManager.getCamera(cameraId)?.let { camera ->
                camera.removeViewer(player)
                viewerManager.removeViewer(player.uuid)

                CobblemonEvents.CAMERA_VIEWER_REMOVED.emit(
                    CameraViewerRemovedEvent(cameraId, camera, player)
                )
            }
        }
    }

    override fun getPlayerCamera(player: ServerPlayer): ICameraController? {
        val cameraId = viewerManager.getCameraForPlayer(player.uuid) ?: return null
        return lifecycleManager.getCamera(cameraId)
    }

    override fun getCamera(cameraId: UUID): ICameraController? {
        return lifecycleManager.getCamera(cameraId)
    }

    override fun tick() {
        lifecycleManager.tick()
    }

    override fun clear() {
        lifecycleManager.clear()
        viewerManager.clear()
        chunkManager.clear()
    }

    internal fun registerViewerChunkView(playerId: UUID, newView: ChunkTrackingView) {
        chunkManager.registerViewerChunkView(playerId, newView)
    }

    internal fun clearViewerChunkView(playerId: UUID) {
        chunkManager.clearViewerChunkView(playerId)
    }

    @JvmStatic
    fun getPlayerChunkView(player: ServerPlayer): ChunkTrackingView? {
        return chunkManager.getPlayerChunkView(player.uuid)
    }

    @JvmStatic
    fun consumeChunkViewUpdate(player: ServerPlayer): ChunkViewUpdate? {
        return chunkManager.consumeChunkViewUpdate(player.uuid)?.let {
            ChunkViewUpdate(it.previous, it.current)
        }
    }

    data class ChunkViewUpdate(val previous: ChunkTrackingView?, val current: ChunkTrackingView?)
}
