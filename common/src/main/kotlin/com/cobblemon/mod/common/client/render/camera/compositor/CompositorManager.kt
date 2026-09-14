/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

object CompositorManager {

    private val compositors = ConcurrentHashMap<UUID, CameraCompositor>()

    var fullscreenCompositorId: UUID? = null
        private set

    fun createCompositor(
        id: UUID,
        layout: CompositorLayout,
        width: Int,
        height: Int,
        fullscreen: Boolean = false
    ): CameraCompositor {
        val compositor = CameraCompositor(id, layout, width, height)
        val previous = compositors.put(id, compositor)
        if (fullscreen) fullscreenCompositorId = id
        else if (fullscreenCompositorId == id) fullscreenCompositorId = null
        previous?.let(::dispose)
        return compositor
    }

    fun setLayout(id: UUID, layout: CompositorLayout) {
        compositors[id]?.layout = layout
    }

    fun removeCompositor(id: UUID) {
        val compositor = compositors.remove(id)
        if (fullscreenCompositorId == id) fullscreenCompositorId = null
        compositor?.let(::dispose)
    }

    fun getCompositor(id: UUID): CameraCompositor? = compositors[id]

    fun compositeAll() {
        for (compositor in compositors.values) {
            compositor.composite()
        }
    }

    @JvmStatic
    fun renderFullscreen() {
        if (!isFullscreenActive()) return
        val compositorId = fullscreenCompositorId ?: return
        val compositor = compositors[compositorId] ?: return

        val mc = Minecraft.getInstance()
        val srcTarget = compositor.getRenderTarget()
        val dstTarget = mc.mainRenderTarget

        val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        try {
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcTarget.frameBufferId)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstTarget.frameBufferId)
            // Hardware blits bypass Iris shader fog injection.
            GL30.glBlitFramebuffer(0, 0, srcTarget.width, srcTarget.height,
                0, 0, dstTarget.width, dstTarget.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    @JvmStatic
    fun getRequiredFeeds(): Set<UUID> {
        if (compositors.isEmpty()) return emptySet()
        val feeds = mutableSetOf<UUID>()
        for ((id, compositor) in compositors) {
            if (SceneEditorOverlay.freePreviewSession()?.ownsCompositor(id) == true) continue
            feeds.addAll(compositor.layout.requiredFeeds())
        }
        return feeds
    }

    @JvmStatic
    fun isFullscreenActive(): Boolean {
        val id = fullscreenCompositorId ?: return false
        if (SceneEditorOverlay.freePreviewSession()?.ownsCompositor(id) == true) return false
        return compositors.containsKey(id)
    }

    fun clear() {
        val previous = compositors.values.toList()
        compositors.clear()
        fullscreenCompositorId = null
        previous.forEach(::dispose)
    }

    private fun dispose(compositor: CameraCompositor) {
        runCatching { compositor.dispose() }
            .onFailure { Cobblemon.LOGGER.warn("Could not dispose camera compositor {}", compositor.compositorId, it) }
    }
}
