/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.client.render.RenderBlendState
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30

internal object CompositorRenderState {
    fun render(target: RenderTarget, width: Int, height: Int, draw: (Matrix4f) -> Unit) {
        val projection = Matrix4f(RenderSystem.getProjectionMatrix())
        val sorting = RenderSystem.getVertexSorting()
        val modelView = Matrix4f(RenderSystem.getModelViewMatrix())
        val shader = RenderSystem.getShader()
        val texture = RenderSystem.getShaderTexture(0)
        val color = RenderSystem.getShaderColor().copyOf()
        val blend = RenderBlendState.capture()
        val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val scissor = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, it) }
        val viewport = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
        val colorMask = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, it) }
        val clearColor = FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
        val clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)
        val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        var boundTextureZero: Int? = if (activeTexture == GL13.GL_TEXTURE0) boundTexture else null
        val fogStart = RenderSystem.getShaderFogStart()
        val fogEnd = RenderSystem.getShaderFogEnd()
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        try {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0)
            if (boundTextureZero == null) boundTextureZero = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            RenderSystem.disableScissor()
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.depthMask(true)
            target.clear(false)
            target.bindWrite(true)
            RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, width.toFloat(), height.toFloat(), 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z)
            stack.identity()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            RenderSystem.disableCull()
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            // Iris fog must not be applied a second time to captured feed textures.
            RenderSystem.setShaderFogStart(Float.MAX_VALUE)
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            draw(Matrix4f().translation(0f, 0f, -18000f))
        } finally {
            stack.popMatrix()
            RenderSystem.getModelViewMatrix().set(modelView)
            RenderSystem.setProjectionMatrix(projection, sorting)
            RenderSystem.setShaderFogStart(fogStart)
            RenderSystem.setShaderFogEnd(fogEnd)
            RenderSystem.setShader { shader }
            RenderSystem.setShaderTexture(0, texture)
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
            RenderSystem.depthMask(depthMask)
            if (depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            blend.restore()
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
            GL11.glClearDepth(clearDepth)
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0)
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3])
            if (!scissorEnabled) RenderSystem.disableScissor()
            boundTextureZero?.let {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0)
                RenderSystem.bindTexture(it)
            }
            RenderSystem.activeTexture(activeTexture)
            RenderSystem.bindTexture(boundTexture)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        }
    }
}
