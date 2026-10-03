/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f

object IrisCompatibility {
    private val api = runCatching { Class.forName("net.irisshaders.iris.api.v0.IrisApi") }.getOrNull()
    private val instance = api?.getMethod("getInstance")?.invoke(null)
    private val shaderPackInUse = api?.getMethod("isShaderPackInUse")
    private val renderingShadowPass = api?.getMethod("isRenderingShadowPass")

    private val bytes = ByteBufferBuilder(1 shl 18)
    private val deferred = mutableListOf<Pair<RenderType, MeshData>>()
    private val modelView = Matrix4f()
    private val projection = Matrix4f()

    fun isShaderPackInUse() = shaderPackInUse?.invoke(instance) == true

    fun isRenderingShadowPass() = renderingShadowPass?.invoke(instance) == true

    fun renderAfterShaderPack(provider: MultiBufferSource, renderType: RenderType, draw: (VertexConsumer) -> Unit) {
        if (!isShaderPackInUse()) return draw(provider.getBuffer(renderType))
        if (isRenderingShadowPass()) return

        if (deferred.isEmpty()) {
            modelView.set(RenderSystem.getModelViewMatrix())
            projection.set(RenderSystem.getProjectionMatrix())
        }
        val builder = BufferBuilder(bytes, renderType.mode(), renderType.format())
        draw(builder)
        builder.build()?.let { deferred += renderType to it }
    }

    fun drawDeferred() {
        if (deferred.isEmpty()) return
        Minecraft.getInstance().mainRenderTarget.bindWrite(false)
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN)
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.set(modelView)
        RenderSystem.applyModelViewMatrix()

        deferred.forEach { (renderType, mesh) -> renderType.draw(mesh) }
        deferred.clear()

        stack.popMatrix()
        RenderSystem.applyModelViewMatrix()
        RenderSystem.restoreProjectionMatrix()
    }
}
