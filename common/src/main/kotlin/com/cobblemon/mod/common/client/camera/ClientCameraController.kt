/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.client.render.models.blockbench.BoneTracker
import com.cobblemon.mod.common.client.camera.BoneFocusResolver
import com.cobblemon.mod.common.net.messages.client.camera.CameraFocusPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.UUID

object ClientCameraController {

    private const val INTERPOLATION_SPEED = 0.15f
    private const val DEFAULT_FEED_RESOLUTION = 960
    private const val MAX_ROTATION_SPEED_DEG_PER_SEC = 120f

    private data class CameraState(
        var currentPosition: Vec3,
        var currentFocusPosition: Vec3?,
        var currentRotation: CameraRotation?,
        var targetPosition: Vec3,
        var targetFocusPosition: Vec3?,
        var targetRotation: CameraRotation?,
        var interpolationProgress: Float = 1f,
        var boneFocusData: BoneFocusData? = null,
        var pendingInstantTransition: Boolean = false
    )

    private data class BoneFocusData(
        val entityId: Int,
        val boneName: String,
        val offset: Vec3,
        val smooth: Boolean
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

    @Volatile
    private var hiddenHudElements: Set<HudElement> = emptySet()

    @JvmStatic
    fun shouldRenderPlayerBody(): Boolean = renderPlayerBody

    /** Returns true when ALL HUD elements are hidden (used by group-level mixin). */
    @JvmStatic
    fun shouldHideHud(): Boolean = hiddenHudElements == HudElement.ALL

    /** Returns true when a specific HUD element should be hidden. */
    @JvmStatic
    fun shouldHideElement(element: HudElement): Boolean = element in hiddenHudElements

    @JvmStatic
    fun updateCamera(
        cameraId: UUID,
        position: Vec3?,
        focusPosition: Vec3?,
        manualRotation: CameraRotation?,
        instantTransition: Boolean = false,
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

        if (instantTransition) {
            camera.currentPosition = position
            camera.currentFocusPosition = focusPosition
            camera.currentRotation = manualRotation
            camera.targetPosition = position
            camera.targetFocusPosition = focusPosition
            camera.targetRotation = manualRotation
            camera.interpolationProgress = 1f
        } else {
            camera.targetPosition = position
            camera.targetFocusPosition = focusPosition
            camera.targetRotation = manualRotation
            camera.interpolationProgress = 0f
        }

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
    fun updateTransform(
        cameraId: UUID,
        position: Vec3,
        rotation: CameraRotation?
    ) {
        val camera = activeCameras[cameraId] ?: run {
            activeCameras[cameraId] = CameraState(
                currentPosition = position,
                currentFocusPosition = null,
                currentRotation = rotation,
                targetPosition = position,
                targetFocusPosition = null,
                targetRotation = rotation
            )
            if (primaryCameraId == null) {
                primaryCameraId = cameraId
            }
            usingDedicatedCamera = true
            return
        }

        camera.targetPosition = position
        camera.targetRotation = rotation
        if (camera.pendingInstantTransition) {
            camera.currentPosition = position
            camera.currentRotation = rotation
        } else {
            camera.interpolationProgress = 0f
        }

        val rotationForFeed = rotation
            ?: camera.currentFocusPosition?.let { CameraInterpolationUtils.calculateLookAtRotation(position, it) }
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
    fun updateFocus(
        cameraId: UUID,
        focus: CameraFocusPacket.FocusData
    ) {
        val camera = activeCameras[cameraId] ?: return

        val oldBoneFocus = camera.boneFocusData
        if (oldBoneFocus != null && focus !is CameraFocusPacket.FocusData.Bone) {
            BoneTracker.release(oldBoneFocus.entityId, oldBoneFocus.boneName)
        }

        when (focus) {
            is CameraFocusPacket.FocusData.None -> {
                camera.targetFocusPosition = null
                camera.boneFocusData = null
                if (camera.pendingInstantTransition) {
                    camera.currentFocusPosition = null
                } else if (camera.currentFocusPosition != null) {
                    camera.interpolationProgress = 0f
                }
            }
            is CameraFocusPacket.FocusData.Point -> {
                camera.targetFocusPosition = focus.position
                camera.boneFocusData = null
                if (camera.pendingInstantTransition) {
                    camera.currentFocusPosition = focus.position
                } else if (camera.currentFocusPosition == null) {
                    camera.currentFocusPosition = focus.position
                } else {
                    camera.interpolationProgress = 0f
                }
            }
            is CameraFocusPacket.FocusData.Entity -> {
                // Entity focus not yet implemented client-side
                camera.targetFocusPosition = null
                camera.boneFocusData = null
            }
            is CameraFocusPacket.FocusData.Bone -> {
                camera.boneFocusData = BoneFocusData(
                    entityId = focus.entityId,
                    boneName = focus.boneName,
                    offset = focus.offset,
                    smooth = focus.smooth
                )
                // Don't set targetFocusPosition - it will be resolved each frame
                camera.targetFocusPosition = null
            }
        }
    }

    @JvmStatic
    fun updateState(
        cameraId: UUID,
        instantTransition: Boolean,
        renderPlayerBody: Boolean,
        hiddenHudElements: Set<HudElement> = emptySet()
    ) {
        this.renderPlayerBody = renderPlayerBody
        this.hiddenHudElements = hiddenHudElements

        if (instantTransition) {
            val camera = activeCameras[cameraId] ?: return
            // Mark pending instant transition - will be applied in tick() after all packets processed
            camera.pendingInstantTransition = true
        }
    }

    @JvmStatic
    fun tick(partialTick: Float) {
        val deltaSeconds = 0.05f

        activeCameras.values.forEach { camera ->
            if (camera.pendingInstantTransition) {
                camera.currentPosition = camera.targetPosition
                camera.currentFocusPosition = camera.targetFocusPosition
                camera.currentRotation = camera.targetRotation
                camera.interpolationProgress = 1f
                camera.pendingInstantTransition = false
            }
            
            val boneFocus = camera.boneFocusData
            if (boneFocus != null) {
                val resolvedBonePosition = BoneFocusResolver.resolveBonePosition(
                    entityId = boneFocus.entityId,
                    boneName = boneFocus.boneName,
                    offset = boneFocus.offset,
                    smooth = boneFocus.smooth,
                    fallback = camera.currentFocusPosition
                )
                camera.currentFocusPosition = resolvedBonePosition
                camera.targetFocusPosition = resolvedBonePosition
            }

            if (camera.interpolationProgress < 1f) {
                camera.interpolationProgress = (camera.interpolationProgress + INTERPOLATION_SPEED).coerceAtMost(1f)

                camera.currentPosition = CameraInterpolationUtils.lerpPosition(
                    camera.currentPosition,
                    camera.targetPosition,
                    camera.interpolationProgress
                )

                // Only interpolate focus if not using bone focus (bone focus updates every frame)
                if (boneFocus == null) {
                    val currentFocus = camera.currentFocusPosition
                    val targetFocus = camera.targetFocusPosition
                    camera.currentFocusPosition = when {
                        currentFocus != null && targetFocus != null ->
                            CameraInterpolationUtils.lerpPosition(currentFocus, targetFocus, camera.interpolationProgress)
                        targetFocus != null -> targetFocus
                        else -> null
                    }
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
            CameraChunkVisibilityManager.updateCameraPosition(primaryId, activeCameras[primaryId]?.currentPosition ?: Vec3.ZERO)
        } else {
            CameraChunkVisibilityManager.clearCameraPosition()
        }

        // Update feeds for ALL cameras (not just primary) so secondary cameras
        // get correct rotation from their resolved focus positions
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val resolution = DEFAULT_FEED_RESOLUTION
        val renderDistance = minecraft.options.effectiveRenderDistance
        val fov = minecraft.options.fov().get().toFloat()

        for ((camId, camera) in activeCameras) {
            val rotation = camera.currentRotation
                ?: camera.currentFocusPosition?.let { focus ->
                    CameraInterpolationUtils.calculateLookAtRotation(camera.currentPosition, focus)
                } ?: CameraRotation(0f, 0f)

            CameraFeedHandler.updateCameraFeed(
                cameraId = camId,
                dimension = level.dimension(),
                position = camera.currentPosition,
                yaw = rotation.yaw,
                pitch = rotation.pitch,
                roll = 0f,
                fov = fov,
                resolution = resolution,
                renderDistance = renderDistance
            )
        }
    }

    @JvmStatic
    fun start(cameraId: UUID, initialPosition: Vec3? = null, hiddenHudElements: Set<HudElement> = emptySet()) {
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
        this.hiddenHudElements = hiddenHudElements
        CameraClientChunkCacheExtension.clear()
    }

    @JvmStatic
    fun stop(cameraId: UUID) {
        activeCameras[cameraId]?.boneFocusData?.let { boneFocus ->
            BoneTracker.release(boneFocus.entityId, boneFocus.boneName)
            BoneFocusResolver.clearSmoothingState(boneFocus.entityId, boneFocus.boneName)
        }

        activeCameras.remove(cameraId)
        val wasPrimary = primaryCameraId == cameraId
        if (wasPrimary) {
            primaryCameraId = null
        }

        if (activeCameras.isEmpty()) {
            usingDedicatedCamera = false
            hiddenHudElements = emptySet()
            CameraClientChunkCacheExtension.clear()
            BoneFocusResolver.clearAll()

            if (wasPrimary) {
                resetMinecraftCamera()
            }
        }
        CameraFeedHandler.removeCamera(cameraId)
    }

    @JvmStatic
    private fun resetMinecraftCamera() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val camera = minecraft.gameRenderer.mainCamera

        camera.setup(
            minecraft.level ?: return,
            player,
            !minecraft.options.cameraType.isFirstPerson,
            minecraft.options.cameraType.isMirrored,
            minecraft.timer.getGameTimeDeltaPartialTick(true)
        )
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
        activeCameras.values.forEach { camera ->
            camera.boneFocusData?.let { boneFocus ->
                BoneTracker.release(boneFocus.entityId, boneFocus.boneName)
            }
        }
        BoneTracker.clear()

        activeCameras.clear()
        primaryCameraId = null
        usingDedicatedCamera = false
        hiddenHudElements = emptySet()
        CameraClientChunkCacheExtension.clear()
        CameraFeedHandler.clear()
        CompositorManager.clear()
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
