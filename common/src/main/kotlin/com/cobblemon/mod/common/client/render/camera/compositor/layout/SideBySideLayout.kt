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
 * Side-by-side or top-bottom layout with two camera feeds.
 *
 * @param leftCameraId  Camera on the left (or top when [vertical] is true)
 * @param rightCameraId Camera on the right (or bottom when [vertical] is true)
 * @param vertical      If true, splits top/bottom instead of left/right
 * @param splitRatio    Position of the split (0-1, default 0.5 = even split)
 * @param gapWidth      Gap between the two feeds in pixels
 */
class SideBySideLayout(
    val leftCameraId: UUID,
    val rightCameraId: UUID,
    val vertical: Boolean = false,
    val splitRatio: Float = 0.5f,
    val gapWidth: Float = 2f
) : CompositorLayout {

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(leftCameraId, rightCameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        val halfGap = gapWidth / 2f

        if (vertical) {
            renderVertical(matrix, width, height, halfGap)
        } else {
            renderHorizontal(matrix, width, height, halfGap)
        }

        if (gapWidth > 0f) {
            drawGap(matrix, width, height, halfGap)
        }
    }

    private fun renderHorizontal(matrix: Matrix4f, width: Float, height: Float, halfGap: Float) {
        val splitX = width * splitRatio

        val leftTarget = CameraFeedHandler.getRenderTarget(leftCameraId)
        if (leftTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, leftTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val x2 = splitX - halfGap
            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f, 1f)
            buf.addVertex(matrix, 0f, height, 0f).setUv(0f, 0f)
            buf.addVertex(matrix, x2, height, 0f).setUv(x2 / width, 0f)
            buf.addVertex(matrix, x2, 0f, 0f).setUv(x2 / width, 1f)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }

        val rightTarget = CameraFeedHandler.getRenderTarget(rightCameraId)
        if (rightTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, rightTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val x1 = splitX + halfGap
            val uStart = x1 / width
            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, x1, 0f, 0f).setUv(uStart, 1f)
            buf.addVertex(matrix, x1, height, 0f).setUv(uStart, 0f)
            buf.addVertex(matrix, width, height, 0f).setUv(1f, 0f)
            buf.addVertex(matrix, width, 0f, 0f).setUv(1f, 1f)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }
    }

    private fun renderVertical(matrix: Matrix4f, width: Float, height: Float, halfGap: Float) {
        val splitY = height * splitRatio

        val topTarget = CameraFeedHandler.getRenderTarget(leftCameraId)
        if (topTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, topTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val y2 = splitY - halfGap
            val vEnd = 1f - (y2 / height)
            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f, 1f)
            buf.addVertex(matrix, 0f, y2, 0f).setUv(0f, vEnd)
            buf.addVertex(matrix, width, y2, 0f).setUv(1f, vEnd)
            buf.addVertex(matrix, width, 0f, 0f).setUv(1f, 1f)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }

        val bottomTarget = CameraFeedHandler.getRenderTarget(rightCameraId)
        if (bottomTarget != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, bottomTarget.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val y1 = splitY + halfGap
            val vStart = 1f - (y1 / height)
            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buf.addVertex(matrix, 0f, y1, 0f).setUv(0f, vStart)
            buf.addVertex(matrix, 0f, height, 0f).setUv(0f, 0f)
            buf.addVertex(matrix, width, height, 0f).setUv(1f, 0f)
            buf.addVertex(matrix, width, y1, 0f).setUv(1f, vStart)
            BufferUploader.drawWithShader(buf.buildOrThrow())
        }
    }

    private fun drawGap(matrix: Matrix4f, width: Float, height: Float, halfGap: Float) {
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        if (vertical) {
            val splitY = height * splitRatio
            buf.addVertex(matrix, 0f, splitY - halfGap, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, 0f, splitY + halfGap, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, width, splitY + halfGap, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, width, splitY - halfGap, 0f).setColor(0, 0, 0, 255)
        } else {
            val splitX = width * splitRatio
            buf.addVertex(matrix, splitX - halfGap, 0f, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, splitX - halfGap, height, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, splitX + halfGap, height, 0f).setColor(0, 0, 0, 255)
            buf.addVertex(matrix, splitX + halfGap, 0f, 0f).setColor(0, 0, 0, 255)
        }
        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    companion object {
        const val TYPE = "side_by_side"
    }
}
