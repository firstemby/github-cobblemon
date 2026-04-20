/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.camera.CameraChunkVisibilityManager
import com.cobblemon.mod.common.client.render.camera.accessor.CameraLevelRendererAccessor
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.mixin.accessor.GameRendererAccessor
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Marker
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CameraFeedHandler {

    data class FeedContext(
        var dimension: ResourceKey<Level>,
        var position: Vec3,
        var yaw: Float,
        var pitch: Float,
        var roll: Float,
        var fov: Float,
        var resolution: Int,
        var renderDistance: Int
    )

    private val contexts: MutableMap<UUID, FeedContext> = ConcurrentHashMap()
    private val feeds: MutableMap<UUID, CameraFeed> = ConcurrentHashMap()

    private var currentlyCapturedCamera: UUID? = null
    private var lastCapturedCamera: UUID? = null
    private var lastCaptureTime: Double = 0.0
    private var debugOverlayEnabled: Boolean = false
    private var cachedMarker: Marker? = null
    private var roundRobinIndex: Int = 0

    @JvmStatic
    fun updateCameraFeed(
        cameraId: UUID,
        dimension: ResourceKey<Level>,
        position: Vec3,
        yaw: Float,
        pitch: Float,
        roll: Float,
        fov: Float,
        resolution: Int,
        renderDistance: Int
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (level.dimension() != dimension) {
            return
        }

        val context = contexts.computeIfAbsent(cameraId) {
            FeedContext(dimension, position, yaw, pitch, roll, fov, resolution, renderDistance)
        }

        context.dimension = dimension
        context.position = position
        context.yaw = yaw
        context.pitch = pitch
        context.roll = roll
        context.fov = fov
        context.resolution = resolution
        context.renderDistance = renderDistance

        val feed = feeds.computeIfAbsent(cameraId) {
            CameraFeed(
                cameraId = cameraId,
                initialPosition = position,
                initialYaw = yaw,
                initialPitch = pitch,
                initialRoll = roll,
                initialFov = fov,
                initialResolution = resolution,
                initialRenderDistance = renderDistance,
                level = level
            )
        }
        feed.updateContext(level, position, yaw, pitch, roll, fov, resolution, renderDistance)
    }

    @JvmStatic
    fun removeCamera(cameraId: UUID) {
        contexts.remove(cameraId)
        feeds.remove(cameraId)?.dispose()
    }

    @JvmStatic
    fun clear() {
        contexts.clear()
        feeds.values.forEach { it.dispose() }
        feeds.clear()
        currentlyCapturedCamera = null
        cachedMarker?.discard()
        cachedMarker = null
        roundRobinIndex = 0
    }

    @JvmStatic
    fun getRenderTarget(cameraId: UUID): RenderTarget? = feeds[cameraId]?.renderTarget()

    @JvmStatic
    fun getTextureId(cameraId: UUID): Int? = feeds[cameraId]?.textureId()

    @JvmStatic
    fun captureFeeds(partialTick: DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = player.level() ?: return

        if (contexts.isEmpty() || feeds.isEmpty()) {
            currentlyCapturedCamera = null
            return
        }

        val currentTime = GLFW.glfwGetTime()
        val activeFeeds = getFeedsToRender()

        if (activeFeeds.isEmpty()) {
            return
        }

        val window = minecraft.getWindow()
        val oldWidth = window.getWidth()
        val oldHeight = window.getHeight()
        val profiler = minecraft.profiler
        val gameRendererAccessor = minecraft.gameRenderer as GameRendererAccessor
        val camera = minecraft.gameRenderer.getMainCamera();
        val oldCameraEntity = minecraft.cameraEntity
        val oldCameraType = minecraft.options.cameraType
        val oldMainRenderTarget = minecraft.mainRenderTarget
        val oldVisibleSections = minecraft.levelRenderer.visibleSections.toMutableList()
        val oldTranslucentTarget = minecraft.levelRenderer.translucentTarget
        val oldItemEntityTarget = minecraft.levelRenderer.itemEntityTarget
        val oldWeatherTarget = minecraft.levelRenderer.weatherTarget
        val oldTransparencyChain = minecraft.levelRenderer.transparencyChain
        val oldEyeHeight = camera.eyeHeight
        val oldEyeHeightO = camera.eyeHeightOld
        val playerFrustum = (minecraft.levelRenderer as CameraLevelRendererAccessor).cobblemonGetFrustum()
        val markerEntity = cachedMarker?.takeIf { it.level() === level }
            ?: Marker(EntityType.MARKER, level).also { cachedMarker = it }
        var capturedAny = false

        val irisActive = IrisCompat.instance.isShadersActive()

        minecraft.gameRenderer.setRenderBlockOutline(false)
        minecraft.gameRenderer.setRenderHand(false)
        minecraft.gameRenderer.setPanoramicMode(true)
        // When Iris shaders are active, keep original window dimensions and
        // mainRenderTarget so Iris's GBuffers (sized at iris$startFrame from
        // original dims) match the projection/viewport. We render to the
        // original mainRenderTarget and blit to feed targets afterward.
        // Round-robin ensures only one renderLevel() per frame with Iris.
        if (!irisActive) {
            val aspectRatio = oldWidth.toFloat() / oldHeight.toFloat().coerceAtLeast(1f)
            window.setWidth((100 * aspectRatio).toInt().coerceAtLeast(1))
            window.setHeight(100)
        }
        minecraft.options.setCameraType(CameraType.FIRST_PERSON)
        // Save ModelView matrix — renderLevel() will overwrite it with camera transforms
        val modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        // Disable fabulous mode targets for compatibility
        minecraft.levelRenderer.translucentTarget = null
        minecraft.levelRenderer.itemEntityTarget = null
        minecraft.levelRenderer.weatherTarget = null
        minecraft.levelRenderer.transparencyChain = null
        minecraft.renderBuffers().bufferSource().endBatch()

        profiler.push("cobblemon:cinematic_feeds")

        for ((cameraId, feed) in activeFeeds) {
            val context = contexts[cameraId] ?: continue
            if (context.dimension != level.dimension()) continue

            val renderTarget = feed.renderTarget()
            val cameraPos = context.position

            Cobblemon.LOGGER.debug("[CameraFeed] Capturing $cameraId at pos=${cameraPos} yaw=${context.yaw} pitch=${context.pitch} rt=${renderTarget.colorTextureId}")

            markerEntity.setPos(cameraPos)
            minecraft.setCameraEntity(markerEntity)
            markerEntity.setXRot(context.pitch);
            markerEntity.setYRot(context.yaw);

            currentlyCapturedCamera = cameraId
            feed.applyVisibleSections(minecraft.levelRenderer.visibleSections)
            profiler.push("cobblemon:discover_frame_sections")
            feed.refreshViewArea(level, context.renderDistance)
            profiler.popPush("cobblemon:bind_frame_target")

            if (irisActive) {
                // Iris path: render to the original mainRenderTarget at window
                // resolution so Iris's GBuffers (sized at iris$startFrame) match.
                // Then blit the result to the feed's smaller RenderTarget.
                oldMainRenderTarget.clear(true)
                oldMainRenderTarget.bindWrite(true)
                minecraft.mainRenderTarget = oldMainRenderTarget
            } else {
                renderTarget.clear(true)
                renderTarget.bindWrite(true)
                minecraft.mainRenderTarget = renderTarget
            }
            profiler.pop()

            try {
                profiler.push("cobblemon:render_camera")
                minecraft.gameRenderer.renderLevel(DeltaTracker.ONE)
                profiler.pop()
            } catch (ex: Exception) {
                Cobblemon.LOGGER.error("Error while rendering camera {} feed", cameraId, ex)
                feed.markForRemoval()
            }

            if (irisActive) {
                // Blit from original mainRenderTarget to the feed's RenderTarget
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldMainRenderTarget.frameBufferId)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, renderTarget.frameBufferId)
                GL30.glBlitFramebuffer(
                    0, 0, oldMainRenderTarget.width, oldMainRenderTarget.height,
                    0, 0, renderTarget.width, renderTarget.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
                )
                oldMainRenderTarget.bindWrite(true)
            }

            val frustum = LevelRenderer.offsetFrustum((minecraft.levelRenderer as CameraLevelRendererAccessor).cobblemonGetFrustum())

            if (!feed.hasVisibleSections() || feed.requiresFrustumUpdate())
                feed.updateVisibleSections(frustum)

            capturedAny = true
            feed.markActive(currentTime)
            lastCapturedCamera = cameraId
            lastCaptureTime = currentTime
            if (debugOverlayEnabled) {
                minecraft.gui.setOverlayMessage(
                    Component.literal(
                        "[CameraFeed] Captured camera $cameraId @ ${context.resolution}x${context.resolution}"
                    ),
                    false
                )
            }
        }

        profiler.pop()

        currentlyCapturedCamera = null

        modelViewStack.popMatrix()

        val restoreEntity = oldCameraEntity ?: player
        minecraft.setCameraEntity(restoreEntity)
        window.setWidth(oldWidth)
        window.setHeight(oldHeight)
        minecraft.levelRenderer.visibleSections.clear()
        minecraft.levelRenderer.visibleSections.addAll(oldVisibleSections)
        camera.eyeHeight = oldEyeHeight
        camera.eyeHeightOld = oldEyeHeightO
        camera.setup(level, restoreEntity, !oldCameraType.isFirstPerson, oldCameraType.isMirrored, if (level.tickRateManager().isEntityFrozen(restoreEntity)) 1.0F else partialTick.getGameTimeDeltaPartialTick(true))
        minecraft.options.setCameraType(oldCameraType)
        minecraft.gameRenderer.setRenderBlockOutline(true)
        minecraft.gameRenderer.setRenderHand(true)
        minecraft.gameRenderer.setPanoramicMode(false)
        minecraft.mainRenderTarget = oldMainRenderTarget
        minecraft.getMainRenderTarget().bindWrite(true)
        minecraft.levelRenderer.translucentTarget = oldTranslucentTarget
        minecraft.levelRenderer.itemEntityTarget = oldItemEntityTarget
        minecraft.levelRenderer.weatherTarget = oldWeatherTarget
        minecraft.levelRenderer.transparencyChain = oldTransparencyChain
        if (debugOverlayEnabled && !capturedAny) {
            minecraft.gui.setOverlayMessage(Component.literal("[CinematicFeed] Idle"), false)
        }

        feeds.entries.removeIf { it.value.shouldBeRemoved() }
    }

    private fun updateCameraInstance(
        minecraft: Minecraft,
        cameraEntity: Entity,
        partialTick: DeltaTracker
    ) {
        val camera = minecraft.gameRenderer.mainCamera
        val currentLevel = minecraft.level ?: return
        camera.setup(
            currentLevel,
            cameraEntity,
            true,
            false,
            partialTick.getGameTimeDeltaPartialTick(false)
        )
    }

    @JvmStatic
    fun isSectionRequested(section: SectionPos): Boolean {
        return feeds.values.any { it.isSectionTracked(section) }
    }

    @JvmStatic
    fun isCapturing(): Boolean = currentlyCapturedCamera != null

    @JvmStatic
    fun hasFeeds(): Boolean = feeds.isNotEmpty()

    @JvmStatic
    fun getCurrentlyCapturedCamera(): UUID? = currentlyCapturedCamera

    @JvmStatic
    fun getLastCapturedCamera(): UUID? = lastCapturedCamera

    @JvmStatic
    fun wasRecentlyCaptured(cameraId: UUID, windowSeconds: Double = 1.0): Boolean {
        val last = lastCapturedCamera ?: return false
        if (last != cameraId) return false
        val now = GLFW.glfwGetTime()
        return now - lastCaptureTime <= windowSeconds
    }

    @JvmStatic
    fun getActiveCameraIds(): Set<UUID> = contexts.keys.toSet()

    @JvmStatic
    fun getFeedContext(cameraId: UUID): FeedContext? = contexts[cameraId]?.copy()

    @JvmStatic
    fun setDebugOverlayEnabled(enabled: Boolean) {
        debugOverlayEnabled = enabled
    }

    @JvmStatic
    fun isDebugOverlayEnabled(): Boolean = debugOverlayEnabled

    @JvmStatic
    fun shouldAddChunk(chunkPos: ChunkPos, playerRenderDistance: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false

        for ((_, context) in contexts) {
            if (context.dimension != level.dimension()) continue

            val cameraChunk = ChunkPos(
                SectionPos.posToSectionCoord(context.position.x),
                SectionPos.posToSectionCoord(context.position.z)
            )
            val maxDistance = maxOf(context.renderDistance, playerRenderDistance) + 1

            if (chunkPos.getChessboardDistance(cameraChunk) <= maxDistance) {
                return true
            }
        }

        return false
    }

    private fun getFeedsToRender(): Map<UUID, CameraFeed> {
        if (feeds.isEmpty()) {
            return emptyMap()
        }

        // When Iris shaders are active, only render ONE feed per frame regardless
        // of compositor mode. Iris's deferred pipeline (GBuffers, shadow maps,
        // composites) doesn't support multiple renderLevel() calls with different
        // cameras in the same frame — depth state from the first render contaminates
        // the second. Round-robin gives each feed full shader quality at half frame rate.
        val irisActive = IrisCompat.instance.isShadersActive()

        // When split-screen is active and Iris is NOT active, vanilla renderLevel
        // is skipped so we can afford to render all feeds for the same GPU budget.
        if (CompositorManager.isFullscreenActive() && !irisActive) {
            return feeds.toMap()
        }

        // Round-robin: render only one feed per frame.
        val feedEntries = feeds.entries.toList()
        roundRobinIndex = roundRobinIndex % feedEntries.size
        val (cameraId, feed) = feedEntries[roundRobinIndex]
        roundRobinIndex++
        return mapOf(cameraId to feed)
    }
}

