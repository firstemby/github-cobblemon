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
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Diagonal split layout extracted from the old SplitScreenCompositor.
 *
 * Draws [primaryCameraId] on the left side of a diagonal line and
 * [secondaryCameraId] on the right, with a configurable angle and gap.
 */
class DiagonalSplitLayout(
    val primaryCameraId: UUID,
    val secondaryCameraId: UUID,
    val splitAngleDegrees: Float = 30f,
    val gapWidth: Float = 2f
) : CompositorLayout {

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(primaryCameraId, secondaryCameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        val centerX = width / 2f
        val angleRad = Math.toRadians(splitAngleDegrees.toDouble()).toFloat()
        val tanAngle = tan(angleRad.toDouble()).toFloat()

        val topX = centerX + tanAngle * (height / 2f)
        val bottomX = centerX - tanAngle * (height / 2f)

        val feedA = CameraFeedHandler.getRenderTarget(primaryCameraId)
        if (feedA != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, feedA.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            drawLeftSide(matrix, width, height, topX, bottomX)
        }

        val feedB = CameraFeedHandler.getRenderTarget(secondaryCameraId)
        if (feedB != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, feedB.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            drawRightSide(matrix, width, height, topX, bottomX)
        }

        RenderSystem.setShaderTexture(0, 0)

        if (gapWidth > 0f) {
            drawGapLine(matrix, width, height, topX, bottomX)
        }
    }

    /** UVs shifted horizontally so the feed center aligns with the triangle centroid. */
    private fun drawLeftSide(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float) {
        val uOffset = 0.25f

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX)

        buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f / screenW + uOffset, 1f)
        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, 0f, screenH, 0f).setUv(0f / screenW + uOffset, 0f)

        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, bottomX, screenH, 0f).setUv(bottomX / screenW + uOffset, 0f)
        buf.addVertex(matrix, 0f, screenH, 0f).setUv(0f / screenW + uOffset, 0f)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    /** UVs shifted so the feed center aligns with the right triangle centroid. */
    private fun drawRightSide(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float) {
        val uOffset = -0.25f

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX)

        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, screenW, 0f, 0f).setUv(1f + uOffset, 1f)
        buf.addVertex(matrix, screenW, screenH, 0f).setUv(1f + uOffset, 0f)

        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, screenW, screenH, 0f).setUv(1f + uOffset, 0f)
        buf.addVertex(matrix, bottomX, screenH, 0f).setUv(bottomX / screenW + uOffset, 0f)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    private fun drawGapLine(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float) {
        val halfGap = gapWidth / 2f
        val dx = topX - bottomX
        val dy = -screenH
        val len = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val nx = -dy / len * halfGap
        val ny = dx / len * halfGap

        RenderSystem.setShader { GameRenderer.getPositionColorShader() }

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        buf.addVertex(matrix, topX - nx, 0f - ny, 0f).setColor(0, 0, 0, 255)
        buf.addVertex(matrix, bottomX - nx, screenH - ny, 0f).setColor(0, 0, 0, 255)
        buf.addVertex(matrix, bottomX + nx, screenH + ny, 0f).setColor(0, 0, 0, 255)
        buf.addVertex(matrix, topX + nx, 0f + ny, 0f).setColor(0, 0, 0, 255)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    companion object {
        const val TYPE = "diagonal_split"
    }
}
