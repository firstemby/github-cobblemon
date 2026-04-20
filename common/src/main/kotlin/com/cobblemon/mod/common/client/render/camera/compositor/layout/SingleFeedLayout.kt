/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor.layout

import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import java.util.UUID

/** Blits a single camera feed full-size into the compositor output. */
class SingleFeedLayout(val cameraId: UUID) : CompositorLayout {

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(cameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        val renderTarget = CameraFeedHandler.getRenderTarget(cameraId) ?: return

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, renderTarget.colorTextureId)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        // UV-flip vertically for OpenGL texture orientation
        buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f, 1f)
        buf.addVertex(matrix, 0f, height, 0f).setUv(0f, 0f)
        buf.addVertex(matrix, width, height, 0f).setUv(1f, 0f)
        buf.addVertex(matrix, width, 0f, 0f).setUv(1f, 1f)
        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    companion object {
        const val TYPE = "single_feed"
    }
}
