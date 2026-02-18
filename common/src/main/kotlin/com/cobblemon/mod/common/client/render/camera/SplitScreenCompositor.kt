/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.Cobblemon
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import java.util.UUID
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Composites two camera feeds with a diagonal split line.
 *
 * Both Camera A and Camera B are drawn from their CameraFeed RenderTargets,
 * giving full control over each side of the split. Camera A covers the left
 * side of the diagonal, Camera B covers the right side.
 */
object SplitScreenCompositor {

    private var active = false
    private var primaryCameraId: UUID = UUID(0, 0)
    private var secondaryCameraId: UUID = UUID(0, 0)
    private var splitAngleDegrees: Float = 30f
    private var gapWidth: Float = 2f

    fun enable(
        primaryCameraId: UUID,
        secondaryCameraId: UUID,
        splitAngleDegrees: Float,
        gapWidth: Float
    ) {
        this.active = true
        this.primaryCameraId = primaryCameraId
        this.secondaryCameraId = secondaryCameraId
        this.splitAngleDegrees = splitAngleDegrees
        this.gapWidth = gapWidth
        Cobblemon.LOGGER.info("[SplitScreen] Enabled: primary=$primaryCameraId, secondary=$secondaryCameraId")
    }

    fun disable() {
        this.active = false
    }

    @JvmStatic
    fun isActive(): Boolean = active

    fun getSecondaryCameraId(): UUID = secondaryCameraId

    /**
     * Renders the split-screen composite using both camera feeds.
     *
     * Camera A's feed is drawn on the left side of the diagonal.
     * Camera B's feed is drawn on the right side of the diagonal.
     * A black border line is drawn along the split edge.
     */
    @JvmStatic
    fun render() {
        if (!active) return

        val mc = Minecraft.getInstance()
        val feedA = CameraFeedHandler.getRenderTarget(primaryCameraId)
        val feedB = CameraFeedHandler.getRenderTarget(secondaryCameraId)

        Cobblemon.LOGGER.debug("[SplitScreen] render: feedA={}, feedB={}", feedA != null, feedB != null)

        val window = mc.window
        val screenW = window.guiScaledWidth.toFloat()
        val screenH = window.guiScaledHeight.toFloat()
        val centerX = screenW / 2f
        val angleRad = Math.toRadians(splitAngleDegrees.toDouble()).toFloat()
        val tanAngle = tan(angleRad.toDouble()).toFloat()

        // Diagonal endpoints: topX is at y=0, bottomX is at y=screenH
        val topX = centerX + tanAngle * (screenH / 2f)
        val bottomX = centerX - tanAngle * (screenH / 2f)

        // Ensure we're drawing to the main framebuffer
        mc.mainRenderTarget.bindWrite(true)

        // Set up GUI-style orthographic projection (matches MC's GUI rendering)
        val ortho = Matrix4f().setOrtho(0f, screenW, screenH, 0f, 1000f, 21000f)
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)

        // Reset ModelView matrix to identity — captureFeeds/renderLevel may have
        // left it as the camera's view transform, which would corrupt our 2D overlay
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.identity()

        val poseStack = PoseStack()
        poseStack.pushPose()
        poseStack.translate(0f, 0f, -18000f)
        val matrix = poseStack.last().pose()

        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        // --- Draw Camera A (left side of diagonal) ---
        // Always draw colored background first, then overlay texture if available
        //drawLeftSideColored(matrix, screenW, screenH, topX, bottomX, 200, 50, 50, 255)
        if (feedA != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, feedA.colorTextureId)
            // Debug: red tint on Camera A to visually distinguish feeds
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            drawLeftSide(matrix, screenW, screenH, topX, bottomX)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        }

        // --- Draw Camera B (right side of diagonal) ---
        //drawRightSideColored(matrix, screenW, screenH, topX, bottomX, 50, 50, 200, 255)
        if (feedB != null) {
            RenderSystem.setShader { GameRenderer.getPositionTexShader() }
            RenderSystem.setShaderTexture(0, feedB.colorTextureId)
            // Debug: blue tint on Camera B to visually distinguish feeds
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            drawRightSide(matrix, screenW, screenH, topX, bottomX)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        }

        RenderSystem.setShaderTexture(0, 0)

        // --- Draw diagonal border line ---
        if (gapWidth > 0f) {
            drawGapLine(matrix, screenW, screenH, topX, bottomX)
        }

        poseStack.popPose()
        modelViewStack.popMatrix()

        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    /**
     * Draws the textured left side (Camera A) — two triangles covering left of the diagonal.
     * UVs are flipped vertically (v=1 at top, v=0 at bottom) to correct for OpenGL texture orientation,
     * and shifted horizontally so the feed center (u=0.5) aligns with the triangle's centroid.
     */
    private fun drawLeftSide(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float) {
        // Left side centroid x = (topX + bottomX) / 4 = screenW / 4 (since topX + bottomX = screenW)
        val uOffset = 0.25f

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX)

        // Triangle 1: top-left → diagonal-top → bottom-left
        buf.addVertex(matrix, 0f, 0f, 0f).setUv(0f / screenW + uOffset, 1f)
        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, 0f, screenH, 0f).setUv(0f / screenW + uOffset, 0f)

        // Triangle 2: diagonal-top → diagonal-bottom → bottom-left
        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, bottomX, screenH, 0f).setUv(bottomX / screenW + uOffset, 0f)
        buf.addVertex(matrix, 0f, screenH, 0f).setUv(0f / screenW + uOffset, 0f)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    /**
     * Draws the textured right side (Camera B) — two triangles covering right of the diagonal.
     * UVs are flipped vertically and shifted so the feed center aligns with the right triangle's centroid.
     */
    private fun drawRightSide(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float) {
        // Right side centroid x = (topX + bottomX + 2*screenW) / 4 = 3*screenW/4
        val uOffset = -0.25f

        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX)

        // Triangle 1: diagonal-top → top-right → bottom-right
        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, screenW, 0f, 0f).setUv(1f + uOffset, 1f)
        buf.addVertex(matrix, screenW, screenH, 0f).setUv(1f + uOffset, 0f)

        // Triangle 2: diagonal-top → bottom-right → diagonal-bottom
        buf.addVertex(matrix, topX, 0f, 0f).setUv(topX / screenW + uOffset, 1f)
        buf.addVertex(matrix, screenW, screenH, 0f).setUv(1f + uOffset, 0f)
        buf.addVertex(matrix, bottomX, screenH, 0f).setUv(bottomX / screenW + uOffset, 0f)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    /** Debug: draws a solid colored left side when feed A is unavailable. */
    private fun drawLeftSideColored(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float, r: Int, g: Int, b: Int, a: Int) {
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }
        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)

        buf.addVertex(matrix, 0f, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, topX, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, 0f, screenH, 0f).setColor(r, g, b, a)

        buf.addVertex(matrix, topX, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, bottomX, screenH, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, 0f, screenH, 0f).setColor(r, g, b, a)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    /** Debug: draws a solid colored right side when feed B is unavailable. */
    private fun drawRightSideColored(matrix: Matrix4f, screenW: Float, screenH: Float, topX: Float, bottomX: Float, r: Int, g: Int, b: Int, a: Int) {
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }
        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)

        buf.addVertex(matrix, topX, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, screenW, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, screenW, screenH, 0f).setColor(r, g, b, a)

        buf.addVertex(matrix, topX, 0f, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, screenW, screenH, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, bottomX, screenH, 0f).setColor(r, g, b, a)

        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    /** Draws the black diagonal border line. */
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
}
