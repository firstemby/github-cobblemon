/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import java.util.UUID

class CameraCompositor(
    val compositorId: UUID,
    var layout: CompositorLayout,
    outputWidth: Int,
    outputHeight: Int
) {
    var outputWidth: Int = outputWidth
        private set
    var outputHeight: Int = outputHeight
        private set

    private var renderTarget: TextureTarget = TextureTarget(outputWidth, outputHeight, true, false)

    fun getTextureId(): Int = renderTarget.colorTextureId

    fun getRenderTarget(): TextureTarget = renderTarget

    fun composite() {
        val mc = Minecraft.getInstance()
        val oldTarget = mc.mainRenderTarget

        renderTarget.clear(false)
        renderTarget.bindWrite(true)

        val ortho = Matrix4f().setOrtho(
            0f, outputWidth.toFloat(),
            outputHeight.toFloat(), 0f,
            1000f, 21000f
        )
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)

        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.identity()

        val poseStack = PoseStack()
        poseStack.pushPose()
        poseStack.translate(0f, 0f, -18000f)
        val matrix = poseStack.last().pose()

        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        // Push fog to infinity so Iris shader fog doesn't affect feed texture draws
        val oldFogStart = RenderSystem.getShaderFogStart()
        val oldFogEnd = RenderSystem.getShaderFogEnd()
        RenderSystem.setShaderFogStart(Float.MAX_VALUE)
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE)

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        layout.render(matrix, outputWidth.toFloat(), outputHeight.toFloat())

        poseStack.popPose()
        modelViewStack.popMatrix()

        RenderSystem.setShaderFogStart(oldFogStart)
        RenderSystem.setShaderFogEnd(oldFogEnd)
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        renderTarget.unbindWrite()
        oldTarget.bindWrite(true)
    }

    fun resize(width: Int, height: Int) {
        if (width != outputWidth || height != outputHeight) {
            outputWidth = width
            outputHeight = height
            renderTarget.destroyBuffers()
            renderTarget = TextureTarget(width, height, true, false)
        }
    }

    fun dispose() {
        renderTarget.destroyBuffers()
    }
}
