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

/**
 * Picture-in-picture layout: a full-screen main feed with a smaller PIP inset.
 *
 * @param mainCameraId   Camera feed displayed full-screen
 * @param pipCameraId    Camera feed displayed in the PIP inset
 * @param pipX           PIP horizontal position (0-1 normalized, 0=left)
 * @param pipY           PIP vertical position (0-1 normalized, 0=top)
 * @param pipWidth       PIP width (0-1 normalized fraction of output width)
 * @param pipHeight      PIP height (0-1 normalized fraction of output height)
 * @param borderWidth    Border width in pixels around the PIP inset
 */
class PictureInPictureLayout(
    val mainCameraId: UUID,
    val pipCameraId: UUID,
    val pipX: Float = 0.7f,
    val pipY: Float = 0.05f,
    val pipWidth: Float = 0.25f,
    val pipHeight: Float = 0.25f,
    val borderWidth: Float = 2f
) : CompositorLayout {

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(mainCameraId, pipCameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        val mainTarget = CameraFeedHandler.getRenderTarget(mainCameraId)
        if (mainTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, mainTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f, 1f)
            buf.addVertex(matrix, 0f, height, 0f).setUv(0f, 0f)
            buf.addVertex(matrix, width, height, 0f).setUv(1f, 0f)
            buf.addVertex(matrix, width, 0f, 0f).setUv(1f, 1f)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }

        val px = pipX * width
        val py = pipY * height
        val pw = pipWidth * width
        val ph = pipHeight * height

        if (borderWidth > 0f) {
            RenderSystem.setShader { GameRenderer.getPositionColorShader() }
            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            val bx1 = px - borderWidth
            val by1 = py - borderWidth
            val bx2 = px + pw + borderWidth
            val by2 = py + ph + borderWidth
            buf.addVertex(matrix, bx1, by1, 0f).setColor(0, 0, 0, 200)
            buf.addVertex(matrix, bx1, by2, 0f).setColor(0, 0, 0, 200)
            buf.addVertex(matrix, bx2, by2, 0f).setColor(0, 0, 0, 200)
            buf.addVertex(matrix, bx2, by1, 0f).setColor(0, 0, 0, 200)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }

        val pipTarget = CameraFeedHandler.getRenderTarget(pipCameraId)
        if (pipTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, pipTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, px, py, 0f).setUv(0f, 1f)
            buf.addVertex(matrix, px, py + ph, 0f).setUv(0f, 0f)
            buf.addVertex(matrix, px + pw, py + ph, 0f).setUv(1f, 0f)
            buf.addVertex(matrix, px + pw, py, 0f).setUv(1f, 1f)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }
    }

    companion object {
        const val TYPE = "picture_in_picture"
    }
}
