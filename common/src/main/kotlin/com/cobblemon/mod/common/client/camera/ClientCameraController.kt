/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.UUID

object ClientCameraController {

    private const val INTERPOLATION_SPEED = 0.15f
    private const val DEFAULT_FEED_RESOLUTION = 512
    private const val MAX_ROTATION_SPEED_DEG_PER_SEC = 120f

    private data class CameraState(
        var currentPosition: Vec3,
        var currentFocusPosition: Vec3?,
        var currentRotation: CameraRotation?,
        var targetPosition: Vec3,
        var targetFocusPosition: Vec3?,
        var targetRotation: CameraRotation?,
        var interpolationProgress: Float = 1f
    )

    data class ActiveTransform(
        val position: Vec3,
        val rotation: CameraRotation
    )

    private val activeCameras = mutableMapOf<UUID, CameraState>()
    private var primaryCameraId: UUID? = null
    private val gameCamera = GameCamera()
    private var usingDedicatedCamera = false

    @Volatile
    private var renderPlayerBody: Boolean = true

    @JvmStatic
    fun shouldRenderPlayerBody(): Boolean = renderPlayerBody

    @JvmStatic
    fun updateCamera(
        cameraId: UUID,
        position: Vec3?,
        focusPosition: Vec3?,
        manualRotation: CameraRotation?,
        renderPlayerBody: Boolean = true
    ) {
        this.renderPlayerBody = renderPlayerBody
        if (position == null) {
            stop(cameraId)
            return
        }

        val camera = activeCameras.getOrPut(cameraId) {
            CameraState(
                currentPosition = position,
                currentFocusPosition = focusPosition,
                currentRotation = manualRotation,
                targetPosition = position,
                targetFocusPosition = focusPosition,
                targetRotation = manualRotation
            )
        }

        camera.targetPosition = position
        camera.targetFocusPosition = focusPosition
        camera.targetRotation = manualRotation
        camera.interpolationProgress = 0f

        if (primaryCameraId == null) {
            primaryCameraId = cameraId
        }
        usingDedicatedCamera = true

        val rotationForFeed = manualRotation
            ?: focusPosition?.let { CameraInterpolationUtils.calculateLookAtRotation(position, it) }
            ?: CameraRotation(0f, 0f)
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level != null) {
            CameraFeedHandler.updateCameraFeed(
                cameraId = cameraId,
                dimension = level.dimension(),
                position = position,
                yaw = rotationForFeed.yaw,
                pitch = rotationForFeed.pitch,
                roll = 0f,
                fov = minecraft.options.fov().get().toFloat(),
                resolution = DEFAULT_FEED_RESOLUTION,
                renderDistance = minecraft.options.effectiveRenderDistance
            )
        }
    }

    @JvmStatic
    fun tick(partialTick: Float) {
        val deltaSeconds = 0.05f

        activeCameras.values.forEach { camera ->
            if (camera.interpolationProgress < 1f) {
                camera.interpolationProgress = (camera.interpolationProgress + INTERPOLATION_SPEED).coerceAtMost(1f)

                camera.currentPosition = CameraInterpolationUtils.lerpPosition(
                    camera.currentPosition,
                    camera.targetPosition,
                    camera.interpolationProgress
                )

                val currentFocus = camera.currentFocusPosition
                val targetFocus = camera.targetFocusPosition
                camera.currentFocusPosition = when {
                    currentFocus != null && targetFocus != null ->
                        CameraInterpolationUtils.lerpPosition(currentFocus, targetFocus, camera.interpolationProgress)
                    targetFocus != null -> targetFocus
                    else -> null
                }

                val currentRot = camera.currentRotation
                val targetRot = camera.targetRotation
                camera.currentRotation = when {
                    currentRot != null && targetRot != null ->
                        CameraInterpolationUtils.slerpRotationClamped(
                            currentRot,
                            targetRot,
                            camera.interpolationProgress,
                            deltaSeconds,
                            MAX_ROTATION_SPEED_DEG_PER_SEC
                        )
                    targetRot != null -> targetRot
                    else -> currentRot
                }
            }
        }

        val primaryId = primaryCameraId
        if (primaryId != null) {
            val primaryCamera = activeCameras[primaryId]
            if (primaryCamera != null) {
                val rotation = primaryCamera.currentRotation
                    ?: primaryCamera.currentFocusPosition?.let { focus ->
                        CameraInterpolationUtils.calculateLookAtRotation(primaryCamera.currentPosition, focus)
                    } ?: CameraRotation(0f, 0f)
                CameraChunkVisibilityManager.updateCameraPosition(primaryId, primaryCamera.currentPosition)

                val minecraft = Minecraft.getInstance()
                val level = minecraft.level ?: return
                val resolution = DEFAULT_FEED_RESOLUTION
                val renderDistance = minecraft.options.effectiveRenderDistance
                val fov = minecraft.options.fov().get().toFloat()
                CameraFeedHandler.updateCameraFeed(
                    cameraId = primaryId,
                    dimension = level.dimension(),
                    position = primaryCamera.currentPosition,
                    yaw = rotation.yaw,
                    pitch = rotation.pitch,
                    roll = 0f,
                    fov = fov,
                    resolution = resolution,
                    renderDistance = renderDistance
                )
            }
        } else {
            CameraChunkVisibilityManager.clearCameraPosition()
        }
    }

    @JvmStatic
    fun start(cameraId: UUID, initialPosition: Vec3? = null) {
        val startPos = initialPosition ?: Minecraft.getInstance().gameRenderer.mainCamera.position
        activeCameras.getOrPut(cameraId) {
            CameraState(
                currentPosition = startPos,
                currentFocusPosition = null,
                currentRotation = null,
                targetPosition = startPos,
                targetFocusPosition = null,
                targetRotation = null
            )
        }
        primaryCameraId = cameraId
        usingDedicatedCamera = true
        CameraClientChunkCacheExtension.clear()
    }

    @JvmStatic
    fun stop(cameraId: UUID) {
        activeCameras.remove(cameraId)
        if (primaryCameraId == cameraId) {
            primaryCameraId = null
        }

        if (activeCameras.isEmpty()) {
            usingDedicatedCamera = false
            CameraClientChunkCacheExtension.clear()
        }
        CameraFeedHandler.removeCamera(cameraId)
    }

    @JvmStatic
    fun getCameraTransform(): ActiveTransform? {
        val cameraId = primaryCameraId ?: return null
        val camera = activeCameras[cameraId] ?: return null

        val rotation = camera.currentRotation
            ?: camera.currentFocusPosition?.let { focus ->
                CameraInterpolationUtils.calculateLookAtRotation(camera.currentPosition, focus)
            }
            ?: CameraRotation(0f, 0f)

        return ActiveTransform(camera.currentPosition, rotation)
    }
    
    @JvmStatic
    fun getActiveCameraTransform(cameraId: UUID): ActiveTransform? {
        val camera = activeCameras[cameraId] ?: return null

        val rotation = camera.currentRotation
            ?: camera.currentFocusPosition?.let { focus ->
                CameraInterpolationUtils.calculateLookAtRotation(camera.currentPosition, focus)
            }
            ?: CameraRotation(0f, 0f)

        return ActiveTransform(camera.currentPosition, rotation)
    }

    @JvmStatic
    fun isActive(): Boolean = primaryCameraId != null && activeCameras.containsKey(primaryCameraId)

    @JvmStatic
    fun getCurrentPosition(): Vec3? = primaryCameraId?.let { activeCameras[it]?.currentPosition }

    @JvmStatic
    fun deactivateAll() {
        activeCameras.clear()
        primaryCameraId = null
        usingDedicatedCamera = false
        CameraClientChunkCacheExtension.clear()
        CameraFeedHandler.clear()
    }

    @JvmStatic
    fun shouldUseCinematicCamera(): Boolean = usingDedicatedCamera && isActive()

    @JvmStatic
    fun prepareCinematicCamera(client: Minecraft, partialTick: Float): GameCamera? {
        if (!shouldUseCinematicCamera()) return null
        gameCamera.prepare(client, partialTick)
        return gameCamera
    }
}
