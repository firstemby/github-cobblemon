/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import kotlin.math.min
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f

object SilkLineRenderer {
    private val TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/tripwire.png")
    private const val WIDTH = 0.5F / 16F
    private const val V0 = 2F / 16F
    private const val V1 = 4F / 16F
    private const val SEGMENT_LENGTH = 4F / 16F

    fun renderStrip(poseStack: PoseStack, buffer: MultiBufferSource, bottom: Float, top: Float, light: Int) {
        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().gameRenderer.mainCamera.yRot))
        val consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE))
        val matrix = poseStack.last().pose()
        val half = WIDTH / 2
        var segmentStart = bottom
        while (segmentStart < top) {
            val segmentEnd = min(segmentStart + SEGMENT_LENGTH, top)
            val u = (segmentEnd - segmentStart) / SEGMENT_LENGTH
            vertex(consumer, matrix, -half, segmentStart, 0F, V0, light)
            vertex(consumer, matrix, -half, segmentEnd, u, V0, light)
            vertex(consumer, matrix, half, segmentEnd, u, V1, light)
            vertex(consumer, matrix, half, segmentStart, 0F, V1, light)
            segmentStart = segmentEnd
        }
        poseStack.popPose()
    }

    private fun vertex(consumer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, u: Float, v: Float, light: Int) {
        consumer.addVertex(matrix, x, y, 0F)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0F, 1F, 0F)
    }
}