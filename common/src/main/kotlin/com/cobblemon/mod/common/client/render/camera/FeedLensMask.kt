package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import kotlin.math.roundToInt

object FeedLensMask {
    data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int)

    fun rectangles(width: Int, height: Int, lens: Lens?, sourceAspect: Double = width.toDouble() / height): List<Rectangle> {
        if (width <= 0 || height <= 0 || lens == null) return emptyList()
        val aspect = sourceAspect.takeIf { it.isFinite() && it > 0.0 } ?: width.toDouble() / height
        val crop = lens.aspectCrop?.toDouble()
        val contentWidth = if (crop != null && crop < aspect) (width * crop / aspect).roundToInt().coerceIn(0, width) else width
        val contentHeight = if (crop != null && crop > aspect) (height * aspect / crop).roundToInt().coerceIn(0, height) else height
        val left = (width - contentWidth) / 2
        val right = left + contentWidth
        val barHeight = (height * lens.letterbox.toDouble()).roundToInt()
        val top = maxOf((height - contentHeight) / 2, barHeight)
        val bottom = minOf((height - contentHeight) / 2 + contentHeight, height - barHeight)
        if (right <= left || bottom <= top) return listOf(Rectangle(0, 0, width, height))
        return buildList {
            if (top > 0) add(Rectangle(0, 0, width, top))
            if (bottom < height) add(Rectangle(0, bottom, width, height - bottom))
            if (left > 0) add(Rectangle(0, top, left, bottom - top))
            if (right < width) add(Rectangle(right, top, width - right, bottom - top))
        }
    }

    fun apply(target: RenderTarget, lens: Lens?, sourceAspect: Double) {
        val masks = rectangles(target.width, target.height, lens, sourceAspect)
        if (masks.isEmpty()) return
        val framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val scissor = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, it) }
        val colorMask = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, it) }
        val clearColor = FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId)
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.clearColor(0f, 0f, 0f, 1f)
            for (mask in masks) {
                RenderSystem.enableScissor(mask.x, target.height - mask.y - mask.height, mask.width, mask.height)
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            }
        } finally {
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0)
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3])
            if (!scissorEnabled) RenderSystem.disableScissor()
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer)
        }
    }
}
