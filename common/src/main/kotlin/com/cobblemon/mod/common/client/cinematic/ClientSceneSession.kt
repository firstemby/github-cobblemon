/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.client.render.camera.compositor.layout.DiagonalSplitLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.PictureInPictureLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SideBySideLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SingleFeedLayout
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

class ClientSceneSession(
    val sceneId: UUID,
    val sceneDefinitionId: String,
    val origin: Vec3,
    val declaredFeeds: List<FeedDeclaration> = emptyList()
) {
    val feeds: MutableMap<String, FeedState> = mutableMapOf()

    private var compositorId: UUID? = null
    fun ownsCompositor(id: UUID): Boolean = compositorId == id
    fun ownsCamera(id: UUID?): Boolean = feeds.values.any { it.feedCameraId == id }
    private var lastActiveFeedIds: Set<String> = emptySet()
    private var beatFeedIds: Set<String>? = null

    var currentBeatId: String? = null
        private set

    var renderPlayerBody: Boolean = true
        private set

    var hiddenHudElements: Set<HudElement> = HudElement.ALL
        private set

    class FeedState(
        val feedCameraId: UUID = UUID.randomUUID(),
        var currentShotId: String? = null,
        var latestTransform: FeedTransform? = null
    )

    fun ensureFeedStarted(feedId: String): FeedState {
        val existing = feeds[feedId]
        if (existing != null) return existing

        val state = FeedState()
        ClientCameraController.start(state.feedCameraId, origin, hiddenHudElements, renderPlayerBody)
        feeds[feedId] = state
        return state
    }

    fun applyFeedTransform(transform: FeedTransform) {
        if (beatFeedIds?.contains(transform.feedId) == false) return
        val state = ensureFeedStarted(transform.feedId)
        state.latestTransform = transform
        ClientCameraController.updateTransform(
            cameraId = state.feedCameraId,
            position = transform.position,
            rotation = transform.rotation,
            roll = transform.roll ?: 0f
        )
        val hint = transform.boneHint
        if (hint != null) {
            ClientCameraController.setBoneFocus(
                cameraId = state.feedCameraId,
                entityId = hint.entityId,
                boneName = hint.boneName,
                offset = hint.offset,
                smooth = hint.smooth,
                yawOffset = hint.yawOffset,
                pitchOffset = hint.pitchOffset
            )
        } else {
            ClientCameraController.clearBoneFocus(state.feedCameraId)
        }
        // Null FOV must clear the prior shot's override and restore the player's configured value.
        ClientCameraController.setFovOverride(state.feedCameraId, transform.fov)
        ClientCameraController.setLens(state.feedCameraId, transform.lens)
    }

    fun applyFeedAssignment(
        feedId: String,
        shotId: String,
        transitionType: FeedAssignmentPacket.TransitionType,
        transitionDurationSeconds: Float
    ) {
        if (beatFeedIds?.contains(feedId) == false) return
        val state = feeds[feedId] ?: ensureFeedStarted(feedId)
        state.currentShotId = shotId

        if (transitionType == FeedAssignmentPacket.TransitionType.INSTANT || transitionDurationSeconds <= 0f) {
            // Hard cuts must bypass the controller's normal interpolation.
            ClientCameraController.markInstantTransitionForScene(state.feedCameraId)
        } else {
            ClientCameraController.setBlendDuration(state.feedCameraId, transitionDurationSeconds)
        }

        refreshCompositor()
    }

    fun refreshCompositor() {
        val activeIds = feeds.filterValues { it.currentShotId != null }.keys
        val existing = compositorId?.let(CompositorManager::getCompositor)
        if (activeIds.isEmpty() && compositorId == null) return
        try {
            if (activeIds == lastActiveFeedIds && existing != null) {
                val window = Minecraft.getInstance().window
                existing.resize(window.width, window.height)
                return
            }
            val layout = chooseLayout(declaredFeeds.filter { it.id in activeIds })
            if (layout == null) {
                val oldId = compositorId
                compositorId = null
                oldId?.let(CompositorManager::removeCompositor)
            } else if (existing != null) {
                val window = Minecraft.getInstance().window
                existing.resize(window.width, window.height)
                existing.layout = layout
            } else {
                val window = Minecraft.getInstance().window
                val id = compositorId ?: UUID.randomUUID()
                CompositorManager.createCompositor(id, layout, window.width, window.height, fullscreen = true)
                compositorId = id
            }
            lastActiveFeedIds = activeIds.toSet()
        } catch (exception: Exception) {
            Cobblemon.LOGGER.error("Could not update cinematic compositor for scene {}", sceneId, exception)
        }
    }

    private fun chooseLayout(active: List<FeedDeclaration>): CompositorLayout? {
        if (active.isEmpty()) return null

        val primary = active.firstOrNull { it.id == "primary" } ?: active.first()
        val primaryCameraId = feeds[primary.id]?.feedCameraId ?: return null

        if (active.size == 1) return SingleFeedLayout(primaryCameraId)

        val diagUpper = active.firstOrNull { it.kind is FeedKind.Diagonal && (it.kind as FeedKind.Diagonal).half == FeedKind.DiagonalHalf.UPPER }
        val diagLower = active.firstOrNull { it.kind is FeedKind.Diagonal && (it.kind as FeedKind.Diagonal).half == FeedKind.DiagonalHalf.LOWER }
        if (diagUpper != null && diagLower != null) {
            val upperId = feeds[diagUpper.id]?.feedCameraId ?: return SingleFeedLayout(primaryCameraId)
            val lowerId = feeds[diagLower.id]?.feedCameraId ?: return SingleFeedLayout(primaryCameraId)
            return DiagonalSplitLayout(
                primaryCameraId = upperId,
                secondaryCameraId = lowerId,
                splitAngleDegrees = 30f,
                gapWidth = 2f
            )
        }

        val splitLeft = active.firstOrNull { it.kind is FeedKind.Split && (it.kind as FeedKind.Split).side == FeedKind.Side.LEFT }
        val splitRight = active.firstOrNull { it.kind is FeedKind.Split && (it.kind as FeedKind.Split).side == FeedKind.Side.RIGHT }
        if (splitLeft != null && splitRight != null) {
            return pairedSplitLayout(splitLeft, splitRight, false) ?: SingleFeedLayout(primaryCameraId)
        }

        val splitTop = active.firstOrNull { it.kind is FeedKind.Split && (it.kind as FeedKind.Split).side == FeedKind.Side.TOP }
        val splitBottom = active.firstOrNull { it.kind is FeedKind.Split && (it.kind as FeedKind.Split).side == FeedKind.Side.BOTTOM }
        if (splitTop != null && splitBottom != null) {
            return pairedSplitLayout(splitTop, splitBottom, true) ?: SingleFeedLayout(primaryCameraId)
        }

        val fullscreen = active.firstOrNull { it.kind is FeedKind.Fullscreen }
        val pip = active.firstOrNull { it.kind is FeedKind.Pip }
        if (fullscreen != null && pip != null) {
            val mainId = feeds[fullscreen.id]?.feedCameraId ?: return SingleFeedLayout(primaryCameraId)
            val pipId = feeds[pip.id]?.feedCameraId ?: return SingleFeedLayout(primaryCameraId)
            val pipKind = pip.kind as FeedKind.Pip
            val margin = minOf(0.05f, (1f - pipKind.size) / 2f)
            val (pipX, pipY) = when (pipKind.corner) {
                FeedKind.Corner.TOP_LEFT -> margin to margin
                FeedKind.Corner.TOP_RIGHT -> (1f - pipKind.size - margin) to margin
                FeedKind.Corner.BOTTOM_LEFT -> margin to (1f - pipKind.size - margin)
                FeedKind.Corner.BOTTOM_RIGHT -> (1f - pipKind.size - margin) to (1f - pipKind.size - margin)
            }
            return PictureInPictureLayout(
                mainCameraId = mainId,
                pipCameraId = pipId,
                pipX = pipX,
                pipY = pipY,
                pipWidth = pipKind.size,
                pipHeight = pipKind.size
            )
        }

        return SingleFeedLayout(primaryCameraId)
    }

    private fun pairedSplitLayout(first: FeedDeclaration, second: FeedDeclaration, vertical: Boolean): SideBySideLayout? {
        val firstCameraId = feeds[first.id]?.feedCameraId ?: return null
        val secondCameraId = feeds[second.id]?.feedCameraId ?: return null
        val firstSize = (first.kind as FeedKind.Split).size
        val secondSize = (second.kind as FeedKind.Split).size
        return SideBySideLayout(
            leftCameraId = firstCameraId,
            rightCameraId = secondCameraId,
            vertical = vertical,
            splitRatio = firstSize / (firstSize + secondSize)
        )
    }

    fun applyBeatChange(
        beatId: String,
        instantTransition: Boolean,
        renderPlayerBody: Boolean,
        hiddenHudElements: Set<HudElement>,
        activeFeedIds: Set<String>
    ) {
        this.currentBeatId = beatId
        beatFeedIds = activeFeedIds.toSet()
        this.renderPlayerBody = renderPlayerBody
        this.hiddenHudElements = hiddenHudElements.toSet()
        ClientCameraController.applyBeatVisualPolicy(
            cameraIds = feeds.values.map { it.feedCameraId },
            hiddenHudElements = hiddenHudElements,
            renderPlayerBody = renderPlayerBody,
            instantTransition = instantTransition
        )
        // Clear inactive shot assignments so the compositor can return to the appropriate feed layout.
        feeds.forEach { (feedId, state) ->
            if (feedId !in activeFeedIds) state.currentShotId = null
        }
        refreshCompositor()
    }

    fun stop() {
        val cameras = feeds.values.map { it.feedCameraId }
        val oldCompositorId = compositorId
        feeds.clear()
        compositorId = null
        currentBeatId = null
        beatFeedIds = null
        lastActiveFeedIds = emptySet()
        for (cameraId in cameras) {
            try {
                ClientCameraController.stop(cameraId)
            } catch (exception: Exception) {
                Cobblemon.LOGGER.error("Could not stop cinematic camera {}", cameraId, exception)
            }
        }
        if (oldCompositorId != null) {
            try {
                CompositorManager.removeCompositor(oldCompositorId)
            } catch (exception: Exception) {
                Cobblemon.LOGGER.error("Could not dispose cinematic compositor {}", oldCompositorId, exception)
            }
        }
    }

}
