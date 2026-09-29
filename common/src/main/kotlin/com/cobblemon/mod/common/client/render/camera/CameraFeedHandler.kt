/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.mojang.blaze3d.systems.RenderSystem
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.client.render.camera.accessor.CameraFeedRenderState
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.CameraType
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.culling.Frustum
import com.cobblemon.mod.common.client.util.compat.SodiumCompat
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Marker
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
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
        var renderDistance: Int,
        var lens: Lens? = null
    )

    private val contexts: MutableMap<UUID, FeedContext> = ConcurrentHashMap()
    private val feeds: MutableMap<UUID, CameraFeed> = ConcurrentHashMap()

    private var currentlyCapturedCamera: UUID? = null
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
        renderDistance: Int,
        lens: Lens? = null
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
        context.lens = lens

        val feed = feeds.computeIfAbsent(cameraId) {
            CameraFeed(
                cameraId = cameraId,
                initialPosition = position,
                initialResolution = resolution,
                initialRenderDistance = renderDistance
            )
        }
        feed.updateContext(position, resolution, renderDistance)
    }

    @JvmStatic
    fun removeCamera(cameraId: UUID) {
        contexts.remove(cameraId)
        feeds.remove(cameraId)?.dispose()
    }

    @JvmStatic
    fun clear() {
        val removed = feeds.values.toList()
        val marker = cachedMarker
        contexts.clear()
        feeds.clear()
        currentlyCapturedCamera = null
        cachedMarker = null
        roundRobinIndex = 0
        removed.forEach { restoreState { it.dispose() } }
        restoreState { marker?.discard() }
    }

    @JvmStatic
    fun getRenderTarget(cameraId: UUID): RenderTarget? = feeds[cameraId]?.renderTarget()

    @JvmStatic
    fun captureFeeds(partialTick: DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = player.level() ?: return

        if (contexts.isEmpty() || feeds.isEmpty()) {
            currentlyCapturedCamera = null
            return
        }

        val activeFeeds = getFeedsToRender()

        if (activeFeeds.isEmpty()) {
            return
        }

        val profiler = minecraft.profiler
        val camera = minecraft.gameRenderer.getMainCamera()
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
        val markerEntity = cachedMarker?.takeIf { it.level() === level }
            ?: Marker(EntityType.MARKER, level).also { cachedMarker = it }

        val rendererState = minecraft.gameRenderer as CameraFeedRenderState
        val oldRenderHand = rendererState.cobblemonRenderHand()
        val oldRenderBlockOutline = rendererState.cobblemonRenderBlockOutline()
        val oldPanoramicMode = minecraft.gameRenderer.isPanoramicMode
        val modelViewStack = RenderSystem.getModelViewStack()
        val oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val oldBoundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        var matrixPushed = false
        var profilerPushed = false
        var blockAtlasId: Int? = null
        var oldAtlasMaxLevel: Int? = null
        try {
            minecraft.gameRenderer.setRenderBlockOutline(false)
            minecraft.gameRenderer.setRenderHand(false)
            minecraft.gameRenderer.setPanoramicMode(true)
            minecraft.options.setCameraType(CameraType.FIRST_PERSON)
            // renderLevel overwrites the player's model-view matrix with each feed's camera transform.
            modelViewStack.pushMatrix()
            matrixPushed = true
            minecraft.levelRenderer.translucentTarget = null
            minecraft.levelRenderer.itemEntityTarget = null
            minecraft.levelRenderer.weatherTarget = null
            minecraft.levelRenderer.transparencyChain = null
            minecraft.renderBuffers().bufferSource().endBatch()

            profiler.push("cobblemon:cinematic_feeds")
            profilerPushed = true
            blockAtlasId = minecraft.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).id
            GlStateManager._bindTexture(blockAtlasId)
            oldAtlasMaxLevel = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL)
            // Texture-level limits also survive Sodium sampler overrides.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

            for ((cameraId, feed) in activeFeeds) {
                val context = contexts[cameraId] ?: continue
                if (context.dimension != level.dimension()) continue

                val renderTarget = feed.renderTarget()
                val cameraPos = context.position

                Cobblemon.LOGGER.debug("[CameraFeed] Capturing $cameraId at pos=${cameraPos} yaw=${context.yaw} pitch=${context.pitch} rt=${renderTarget.colorTextureId}")

                // moveTo initializes old-tick coordinates too, preventing camera interpolation from sampling fog at the wrong position.
                markerEntity.moveTo(cameraPos.x, cameraPos.y, cameraPos.z, context.yaw, context.pitch)
                minecraft.setCameraEntity(markerEntity)
                currentlyCapturedCamera = cameraId

                // Initialize the camera before rendering so fog and biome tint use the current feed's position.
                camera.setup(
                    level,
                    markerEntity,
                    false,
                    false,
                    if (level.tickRateManager().isEntityFrozen(markerEntity)) 1.0F else partialTick.getGameTimeDeltaPartialTick(true)
                )

                // Re-dirty the lightmap for every feed; a previous render may have consumed the update flag.
                minecraft.gameRenderer.lightTexture().tick()

                oldMainRenderTarget.clear(true)
                oldMainRenderTarget.bindWrite(true)
                minecraft.mainRenderTarget = oldMainRenderTarget

                profiler.push("cobblemon:render_camera")
                try {
                    minecraft.gameRenderer.renderLevel(DeltaTracker.ONE)
                } catch (ex: Exception) {
                    Cobblemon.LOGGER.error("Error while rendering camera {} feed", cameraId, ex)
                    feed.markForRemoval()
                    continue
                } finally {
                    profiler.pop()
                }

                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldMainRenderTarget.frameBufferId)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, renderTarget.frameBufferId)
                GL30.glBlitFramebuffer(
                        0, 0, oldMainRenderTarget.width, oldMainRenderTarget.height,
                        0, 0, renderTarget.width, renderTarget.height,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
                )
                FeedLensMask.apply(renderTarget, context.lens, oldMainRenderTarget.width.toDouble() / oldMainRenderTarget.height)
                oldMainRenderTarget.bindWrite(true)


            }
        } finally {
            currentlyCapturedCamera = null
            minecraft.mainRenderTarget = oldMainRenderTarget
            minecraft.levelRenderer.translucentTarget = oldTranslucentTarget
            minecraft.levelRenderer.itemEntityTarget = oldItemEntityTarget
            minecraft.levelRenderer.weatherTarget = oldWeatherTarget
            minecraft.levelRenderer.transparencyChain = oldTransparencyChain
            camera.eyeHeight = oldEyeHeight
            camera.eyeHeightOld = oldEyeHeightO
            restoreState { minecraft.gameRenderer.setRenderBlockOutline(oldRenderBlockOutline) }
            restoreState { minecraft.gameRenderer.setRenderHand(oldRenderHand) }
            restoreState { minecraft.gameRenderer.setPanoramicMode(oldPanoramicMode) }
            restoreState { minecraft.options.setCameraType(oldCameraType) }
            if (matrixPushed) restoreState { modelViewStack.popMatrix() }
            if (profilerPushed) restoreState { profiler.pop() }
            restoreState { GlStateManager._activeTexture(oldActiveTexture) }
            val atlasId = blockAtlasId
            val maxLevel = oldAtlasMaxLevel
            if (atlasId != null && maxLevel != null) restoreState {
                GlStateManager._bindTexture(atlasId)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel)
            }
            restoreState { GlStateManager._bindTexture(oldBoundTexture) }
            restoreState { oldMainRenderTarget.bindWrite(true) }
            restoreState {
                minecraft.levelRenderer.visibleSections.clear()
                minecraft.levelRenderer.visibleSections.addAll(oldVisibleSections)
            }
            val restoreEntity = oldCameraEntity ?: player
            restoreState { minecraft.setCameraEntity(restoreEntity) }
            restoreState {
                camera.setup(level, restoreEntity, !oldCameraType.isFirstPerson, oldCameraType.isMirrored,
                    if (level.tickRateManager().isEntityFrozen(restoreEntity)) 1.0F else partialTick.getGameTimeDeltaPartialTick(true))
            }
            feeds.entries.filter { it.value.shouldBeRemoved() }.map { it.key }.forEach { id ->
                restoreState { removeCamera(id) }
            }
        }
    }

    @JvmStatic
    fun prepareVisibleSections(frustum: Frustum) {
        if (SodiumCompat.instance.isActive()) return
        val cameraId = currentlyCapturedCamera ?: return
        val feed = feeds[cameraId] ?: return
        val context = contexts[cameraId] ?: return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        feed.refreshViewArea(level, context.renderDistance)
        feed.updateVisibleSections(LevelRenderer.offsetFrustum(frustum))
        feed.applyVisibleSections(minecraft.levelRenderer.visibleSections)
    }

    @JvmStatic
    fun isCapturing(): Boolean = currentlyCapturedCamera != null

    @JvmStatic
    fun hasFeeds(): Boolean = feeds.isNotEmpty()

    @JvmStatic
    fun getCurrentlyCapturedCamera(): UUID? = currentlyCapturedCamera

    @JvmStatic
    fun getFeedContext(cameraId: UUID): FeedContext? = contexts[cameraId]?.copy()

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

    internal fun getFeedsToRender(): Map<UUID, CameraFeed> {
        val dimension = Minecraft.getInstance().level?.dimension() ?: return emptyMap()
        val required = CompositorManager.getRequiredFeeds()
        val candidates = feeds.filterKeys { it in required && contexts[it]?.dimension == dimension }
        if (candidates.isEmpty()) return emptyMap()
        if (CompositorManager.isFullscreenActive() && !IrisCompat.instance.isShadersActive()) return candidates
        val entries = candidates.entries.toList()
        roundRobinIndex %= entries.size
        val (cameraId, feed) = entries[roundRobinIndex++]
        return mapOf(cameraId to feed)
    }

    private inline fun restoreState(action: () -> Unit) {
        try {
            action()
        } catch (exception: Exception) {
            Cobblemon.LOGGER.error("Could not restore camera render state", exception)
        }
    }
}
