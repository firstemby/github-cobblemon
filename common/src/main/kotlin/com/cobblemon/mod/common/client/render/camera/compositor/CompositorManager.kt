/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all active [CameraCompositor] instances and handles full-screen display.
 *
 * One compositor can be designated as the "fullscreen" compositor, whose output
 * replaces the vanilla level rendering on screen.
 */
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
        removeCompositor(id)
        val compositor = CameraCompositor(id, layout, width, height)
        compositors[id] = compositor
        if (fullscreen) {
            fullscreenCompositorId = id
        }
        return compositor
    }

    fun setLayout(id: UUID, layout: CompositorLayout) {
        compositors[id]?.layout = layout
    }

    fun removeCompositor(id: UUID) {
        val compositor = compositors.remove(id)
        compositor?.dispose()
        if (fullscreenCompositorId == id) {
            fullscreenCompositorId = null
        }
    }

    fun getCompositor(id: UUID): CameraCompositor? = compositors[id]

    fun compositeAll() {
        for (compositor in compositors.values) {
            compositor.composite()
        }
    }

    /**
     * Blits the fullscreen compositor's output texture to the main render target.
     * Uses glBlitFramebuffer (hardware copy) to bypass Iris shader fog injection.
     */
    @JvmStatic
    fun renderFullscreen() {
        val compositorId = fullscreenCompositorId ?: return
        val compositor = compositors[compositorId] ?: return

        val mc = Minecraft.getInstance()
        val srcTarget = compositor.getRenderTarget()
        val dstTarget = mc.mainRenderTarget

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcTarget.frameBufferId)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstTarget.frameBufferId)
        GL30.glBlitFramebuffer(
            0, 0, srcTarget.width, srcTarget.height,
            0, 0, dstTarget.width, dstTarget.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        )
        dstTarget.bindWrite(true)
    }

    /**
     * Returns the union of all camera feed UUIDs required by active compositors.
     * Used by [CameraFeedHandler] to determine which feeds need to be captured.
     */
    @JvmStatic
    fun getRequiredFeeds(): Set<UUID> {
        if (compositors.isEmpty()) return emptySet()
        val feeds = mutableSetOf<UUID>()
        for (compositor in compositors.values) {
            feeds.addAll(compositor.layout.requiredFeeds())
        }
        return feeds
    }

    @JvmStatic
    fun isFullscreenActive(): Boolean {
        val id = fullscreenCompositorId ?: return false
        return compositors.containsKey(id)
    }

    /** Called on disconnect/dimension change. */
    fun clear() {
        for (compositor in compositors.values) {
            compositor.dispose()
        }
        compositors.clear()
        fullscreenCompositorId = null
    }
}
