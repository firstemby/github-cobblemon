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
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.client.cinematic.CinematicClientExtensions
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

object ClientCameraController {

    private const val INTERPOLATION_SECONDS = 0.1f
    private const val DEFAULT_FEED_RESOLUTION = 1920
    private const val MAX_ROTATION_SPEED_DEG_PER_SEC = 120f

    private data class CameraState(
        var currentPosition: Vec3,
        var currentFocusPosition: Vec3?,
        var currentRotation: CameraRotation?,
        var targetPosition: Vec3,
        var targetFocusPosition: Vec3?,
        var targetRotation: CameraRotation?,
        var startPosition: Vec3 = currentPosition,
        var startFocusPosition: Vec3? = currentFocusPosition,
        var startRotation: CameraRotation? = currentRotation,
        var elapsedSeconds: Double = INTERPOLATION_SECONDS.toDouble(),
        var blendDurationSeconds: Float? = null,
        var waitingForPose: Boolean = false,
        var boneFocusData: BoneFocusData? = null,
        var pendingInstantTransition: Boolean = false,
        var fovOverride: Float? = null,
        var targetFovOverride: Float? = fovOverride,
        var startFovOverride: Float? = fovOverride,
        var lens: Lens? = null,
        var currentRoll: Float = 0f,
        var targetRoll: Float = currentRoll,
        var startRoll: Float = currentRoll,
        var hiddenHudElements: Set<HudElement> = emptySet(),
        var renderPlayerBody: Boolean = true
    )

    private data class BoneFocusData(
        val entityId: Int,
        val boneName: String,
        val offset: Vec3,
        val smooth: Boolean,
        val yawOffset: Float,
        val pitchOffset: Float,
        val resolver: BoneFocusResolver = BoneFocusResolver(entityId, boneName)
    )

    data class ActiveTransform(
        val position: Vec3,
        val rotation: CameraRotation,
        val roll: Float = 0f
    )

    private val activeCameras = mutableMapOf<UUID, CameraState>()
    private var primaryCameraId: UUID? = null
    private var usingDedicatedCamera = false

    private fun visualPolicyCamera(): CameraState? {
        val capturedId = CameraFeedHandler.getCurrentlyCapturedCamera()
        if (capturedId != null) return activeCameras[capturedId]
        if (CompositorManager.isFullscreenActive()) {
            val compositor = CompositorManager.fullscreenCompositorId?.let(CompositorManager::getCompositor)
            compositor?.layout?.requiredFeeds()?.firstNotNullOfOrNull(activeCameras::get)?.let { return it }
        }
        return activeCameras[primaryCameraId]
    }

    @JvmStatic
    fun shouldRenderPlayerBody(): Boolean = visualPolicyCamera()?.renderPlayerBody ?: true

    @JvmStatic
    fun shouldHideHud(): Boolean = visualPolicyCamera()?.hiddenHudElements == HudElement.ALL

    @JvmStatic
    fun shouldHideElement(element: HudElement): Boolean = visualPolicyCamera()?.hiddenHudElements?.contains(element) == true

    @JvmStatic
    fun updateTransform(
        cameraId: UUID,
        position: Vec3,
        rotation: CameraRotation?,
        roll: Float = 0f
    ) {
        val camera = activeCameras[cameraId] ?: run {
            activeCameras[cameraId] = CameraState(
                currentPosition = position,
                currentFocusPosition = null,
                currentRotation = rotation,
                targetPosition = position,
                targetFocusPosition = null,
                targetRotation = rotation,
                currentRoll = roll
            )
            if (primaryCameraId == null) {
                primaryCameraId = cameraId
            }
            usingDedicatedCamera = true
            return
        }

        beginPoseUpdate(camera)
        camera.targetPosition = position
        camera.targetRotation = rotation
        camera.targetRoll = roll
    }

    private fun beginPoseUpdate(camera: CameraState) {
        if (camera.blendDurationSeconds == null) {
            captureStart(camera)
        }
        camera.waitingForPose = false
    }

    private fun captureStart(camera: CameraState) {
        camera.startPosition = camera.currentPosition
        camera.startFocusPosition = camera.currentFocusPosition
        camera.startRotation = camera.currentRotation
        camera.startRoll = camera.currentRoll
        camera.startFovOverride = camera.fovOverride
        camera.elapsedSeconds = 0.0
    }

    private fun requestInstantTransition(camera: CameraState) {
        camera.pendingInstantTransition = true
        camera.blendDurationSeconds = null
        camera.waitingForPose = true
    }

    private fun snapToTarget(camera: CameraState) {
        camera.currentPosition = camera.targetPosition
        camera.currentFocusPosition = camera.targetFocusPosition
        camera.currentRotation = camera.targetRotation
        camera.currentRoll = camera.targetRoll
        camera.fovOverride = camera.targetFovOverride
        captureStart(camera)
        camera.elapsedSeconds = INTERPOLATION_SECONDS.toDouble()
        camera.blendDurationSeconds = null
        camera.waitingForPose = false
        camera.pendingInstantTransition = false
    }

    // Changing the entity or bone must release prior tracking and reset its smoothing state.
    @JvmStatic
    fun setBoneFocus(
        cameraId: UUID,
        entityId: Int,
        boneName: String,
        offset: Vec3,
        smooth: Boolean,
        yawOffset: Float = 0f,
        pitchOffset: Float = 0f
    ) {
        val camera = activeCameras[cameraId] ?: return
        val prior = camera.boneFocusData
        if (prior != null && (prior.entityId != entityId || prior.boneName != boneName)) {
            prior.resolver.close()
        }
        camera.boneFocusData = prior?.takeIf { it.entityId == entityId && it.boneName == boneName }
            ?.copy(offset = offset, smooth = smooth, yawOffset = yawOffset, pitchOffset = pitchOffset)
            ?: BoneFocusData(entityId, boneName, offset, smooth, yawOffset, pitchOffset)
    }

    @JvmStatic
    fun clearBoneFocus(cameraId: UUID) {
        val camera = activeCameras[cameraId] ?: return
        val prior = camera.boneFocusData ?: return
        prior.resolver.close()
        camera.boneFocusData = null
    }

    @JvmStatic
    fun setFovOverride(cameraId: UUID, fov: Float?) {
        activeCameras[cameraId]?.targetFovOverride = fov
    }

    fun setLens(cameraId: UUID, lens: Lens?) {
        activeCameras[cameraId]?.lens = lens
    }

    @JvmStatic
    fun getPrimaryFovOverride(): Float? {
        val id = primaryCameraId ?: return null
        return activeCameras[id]?.fovOverride
    }

    @JvmStatic
    fun setBlendDuration(cameraId: UUID, durationSeconds: Float) {
        require(durationSeconds.isFinite() && durationSeconds >= 0f)
        val camera = activeCameras[cameraId] ?: return
        if (durationSeconds == 0f) {
            requestInstantTransition(camera)
            return
        }
        captureStart(camera)
        camera.blendDurationSeconds = durationSeconds
        camera.waitingForPose = true
        camera.pendingInstantTransition = false
    }

    @JvmStatic
    fun advanceFrame(deltaSeconds: Float) {
        val elapsed = deltaSeconds.takeIf { it.isFinite() && it > 0f } ?: 0f

        activeCameras.values.forEach { camera ->
            val instantTransition = camera.pendingInstantTransition && !camera.waitingForPose
            if (instantTransition) {
                snapToTarget(camera)
            }

            val boneFocus = camera.boneFocusData
            if (boneFocus != null) {
                val resolvedBonePosition = boneFocus.resolver.resolve(
                    offset = boneFocus.offset,
                    smooth = boneFocus.smooth,
                    fallback = camera.currentFocusPosition,
                    deltaSeconds = elapsed
                )
                camera.currentFocusPosition = resolvedBonePosition
                camera.targetFocusPosition = resolvedBonePosition
            }

            if (!camera.waitingForPose) {
                val blendDuration = camera.blendDurationSeconds
                val duration = blendDuration ?: INTERPOLATION_SECONDS
                camera.elapsedSeconds = (camera.elapsedSeconds + elapsed).coerceAtMost(duration.toDouble())
                val progress = (camera.elapsedSeconds / duration).toFloat().coerceIn(0f, 1f)
                camera.currentPosition = CameraInterpolationUtils.lerpPosition(
                    camera.startPosition, camera.targetPosition, progress
                )
                camera.currentRoll = CameraInterpolationUtils.lerpAngle(camera.startRoll, camera.targetRoll, progress)
                val startFov = camera.startFovOverride
                val targetFov = camera.targetFovOverride
                camera.fovOverride = when {
                    progress == 1f -> targetFov
                    startFov == null && targetFov == null -> null
                    else -> {
                        val from = startFov ?: Minecraft.getInstance().options.fov().get().toFloat()
                        val to = targetFov ?: Minecraft.getInstance().options.fov().get().toFloat()
                        from + (to - from) * progress
                    }
                }

                if (boneFocus == null) {
                    val startFocus = camera.startFocusPosition
                    val targetFocus = camera.targetFocusPosition
                    camera.currentFocusPosition = when {
                        startFocus != null && targetFocus != null ->
                            CameraInterpolationUtils.lerpPosition(startFocus, targetFocus, progress)
                        else -> targetFocus
                    }
                }

                val startRotation = camera.startRotation
                val focusPosition = camera.currentFocusPosition
                val targetRotation = if (boneFocus != null && focusPosition != null) {
                    val aim = CameraInterpolationUtils.calculateLookAtRotation(camera.currentPosition, focusPosition)
                    CameraRotation(aim.yaw + boneFocus.yawOffset, aim.pitch + boneFocus.pitchOffset)
                } else camera.targetRotation
                val desiredRotation = when {
                    startRotation != null && targetRotation != null ->
                        CameraInterpolationUtils.lerpRotation(startRotation, targetRotation, progress)
                    else -> targetRotation
                }
                val currentRotation = camera.currentRotation
                camera.currentRotation = when {
                    desiredRotation == null -> null
                    instantTransition || blendDuration != null || currentRotation == null -> desiredRotation
                    else -> CameraInterpolationUtils.lerpRotationClamped(
                        currentRotation, desiredRotation, 1f, elapsed, MAX_ROTATION_SPEED_DEG_PER_SEC
                    )
                }
                if (blendDuration != null && progress == 1f) {
                    camera.blendDurationSeconds = null
                    captureStart(camera)
                    camera.elapsedSeconds = INTERPOLATION_SECONDS.toDouble()
                }
            }
        }

        val primaryId = primaryCameraId
        if (primaryId != null) {
            CameraChunkVisibilityManager.updateCameraPosition(activeCameras[primaryId]?.currentPosition ?: Vec3.ZERO)
        } else {
            CameraChunkVisibilityManager.clearCameraPosition()
        }

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
                roll = camera.currentRoll,
                fov = camera.fovOverride ?: fov,
                resolution = resolution,
                renderDistance = renderDistance,
                lens = camera.lens
            )
        }
    }

    @JvmStatic
    fun start(
        cameraId: UUID,
        initialPosition: Vec3? = null,
        hiddenHudElements: Set<HudElement> = emptySet(),
        renderPlayerBody: Boolean = true
    ) {
        val wasInactive = activeCameras.isEmpty()
        val startPos = initialPosition ?: Minecraft.getInstance().gameRenderer.mainCamera.position
        val camera = activeCameras.getOrPut(cameraId) {
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
        camera.hiddenHudElements = hiddenHudElements.toSet()
        camera.renderPlayerBody = renderPlayerBody
        if (wasInactive) CameraClientChunkCacheExtension.clear()
    }

    @JvmStatic
    fun stop(cameraId: UUID) {
        val camera = activeCameras.remove(cameraId)
        val wasPrimary = primaryCameraId == cameraId
        if (wasPrimary) {
            primaryCameraId = activeCameras.keys.lastOrNull()
        }

        try {
            if (activeCameras.isEmpty()) {
                usingDedicatedCamera = false
                CameraClientChunkCacheExtension.clear()
                if (wasPrimary) resetMinecraftCamera()
            }
        } finally {
            try {
                camera?.boneFocusData?.resolver?.close()
            } finally {
                CameraFeedHandler.removeCamera(cameraId)
            }
        }
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

        return ActiveTransform(camera.currentPosition, rotation, camera.currentRoll)
    }

    @JvmStatic
    fun getActiveCameraTransform(cameraId: UUID): ActiveTransform? {
        val camera = activeCameras[cameraId] ?: return null

        val rotation = camera.currentRotation
            ?: camera.currentFocusPosition?.let { focus ->
                CameraInterpolationUtils.calculateLookAtRotation(camera.currentPosition, focus)
            }
            ?: CameraRotation(0f, 0f)

        return ActiveTransform(camera.currentPosition, rotation, camera.currentRoll)
    }

    @JvmStatic
    fun isActive(): Boolean = primaryCameraId != null && activeCameras.containsKey(primaryCameraId)

    @JvmStatic
    fun applyBeatVisualPolicy(
        cameraIds: Collection<UUID>,
        hiddenHudElements: Set<HudElement>,
        renderPlayerBody: Boolean,
        instantTransition: Boolean
    ) {
        val hiddenElements = hiddenHudElements.toSet()
        for (cameraId in cameraIds) {
            val camera = activeCameras[cameraId] ?: continue
            camera.hiddenHudElements = hiddenElements
            camera.renderPlayerBody = renderPlayerBody
            if (instantTransition) requestInstantTransition(camera)
        }
    }

    @JvmStatic
    fun markInstantTransitionForScene(cameraId: UUID) {
        activeCameras[cameraId]?.let(::requestInstantTransition)
    }

    @JvmStatic
    fun deactivateAll() {
        activeCameras.values.forEach { camera ->
            camera.boneFocusData?.resolver?.close()
        }

        activeCameras.clear()
        primaryCameraId = null
        usingDedicatedCamera = false
        CameraClientChunkCacheExtension.clear()
        CameraFeedHandler.clear()
        CompositorManager.clear()
    }

    @JvmStatic
    fun shouldUseCinematicCamera(): Boolean = usingDedicatedCamera && isActive() &&
        (CameraFeedHandler.isCapturing() ||
            !CinematicClientExtensions.suppressMainCamera())

}
