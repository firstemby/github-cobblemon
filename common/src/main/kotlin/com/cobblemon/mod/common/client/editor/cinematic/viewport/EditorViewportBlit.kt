/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

object EditorViewportBlit {

    fun blit(mainTarget: RenderTarget) {
        if (!EditorViewportState.active) return

        // Real physical framebuffer size — bypasses the WindowMixin getWidth/getHeight override.
        val handle = Minecraft.getInstance().window.window
        val wbuf = IntArray(1)
        val hbuf = IntArray(1)
        GLFW.glfwGetFramebufferSize(handle, wbuf, hbuf)
        val realW = wbuf[0].coerceAtLeast(1)
        val realH = hbuf[0].coerceAtLeast(1)

        val vpW = EditorViewportState.windowWidth.coerceAtLeast(1).toFloat()
        val vpH = EditorViewportState.windowHeight.coerceAtLeast(1).toFloat()
        val fracL = EditorViewportState.frameX / vpW
        val fracT = EditorViewportState.frameY / vpH
        val fracR = (EditorViewportState.frameX + EditorViewportState.frameWidth) / vpW
        val fracB = (EditorViewportState.frameY + EditorViewportState.frameHeight) / vpH

        val dstX0 = (fracL * realW).toInt()
        val dstX1 = (fracR * realW).toInt()
        // GL framebuffers use a bottom-left origin; the ImGui rect is top-left, so flip Y.
        val dstY0 = (realH - fracB * realH).toInt()
        val dstY1 = (realH - fracT * realH).toInt()

        GlStateManager._viewport(0, 0, realW, realH)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainTarget.frameBufferId)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0)
        GlStateManager._glBlitFrameBuffer(
            0, 0, mainTarget.width, mainTarget.height,
            dstX0, dstY0, dstX1, dstY1,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        )
        // Leave the screen (fb 0) bound for the subsequent ImGui draw, as the real blit would.
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }
}
