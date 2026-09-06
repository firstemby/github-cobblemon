/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import com.example.mod.entity.RealtimeMoveAreaEffect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class RealtimeAoeEntityRenderer(
    context: EntityRendererProvider.Context
) : EntityRenderer<RealtimeMoveAreaEffect>(context) {

    override fun render(
        entity: RealtimeMoveAreaEffect,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        val height = 0.5f
        // Move origin to entity center
        poseStack.translate(0.0, height * 0.5, 0.0)

        val consumer = buffer.getBuffer(RenderType.lines())
        // TODO: AOE shape differentiation
//        if (entity.renderSphere) {
//            drawSphere(
//                poseStack,
//                consumer,
//                entity.radius,
//                32,
//                1f, 0f, 0f, 1f
//            )
//        } else {
            drawCylinder(
                poseStack,
                consumer,
                entity.radius.toFloat(),
                height,//entity.height.toFloat(),
                32,
                0f, 1f, 0f, 1f
            )
//        }

        poseStack.popPose()

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight)
    }

    override fun getTextureLocation(entity: RealtimeMoveAreaEffect): ResourceLocation {
        return TEXTURE_LOCATION
    }

    companion object {
        // no-op really
        private val TEXTURE_LOCATION: ResourceLocation =
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png")
    }

    private fun line(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val pose = poseStack.last()

        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1

        val length = sqrt(dx * dx + dy * dy + dz * dz)

        val nx = dx / length
        val ny = dy / length
        val nz = dz / length

        consumer.addVertex(pose.pose(), x1, y1, z1)
            .setColor(r, g, b, a)
            .setNormal(pose, nx, ny, nz)

        consumer.addVertex(pose.pose(), x2, y2, z2)
            .setColor(r, g, b, a)
            .setNormal(pose, nx, ny, nz)
    }

    private fun drawSphere(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        radius: Float,
        segments: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val step = (Math.PI * 2.0 / segments)

        for (i in 0 until segments) {

            val a0 = (i * step).toFloat()
            val a1 = ((i + 1) * step).toFloat()

            val c0 = cos(a0)
            val s0 = sin(a0)

            val c1 = cos(a1)
            val s1 = sin(a1)

            // XZ circle
            line(
                poseStack, consumer,
                radius * c0, 0f, radius * s0,
                radius * c1, 0f, radius * s1,
                r, g, b, a
            )

            // XY circle
            line(
                poseStack, consumer,
                radius * c0, radius * s0, 0f,
                radius * c1, radius * s1, 0f,
                r, g, b, a
            )

            // YZ circle
            line(
                poseStack, consumer,
                0f, radius * c0, radius * s0,
                0f, radius * c1, radius * s1,
                r, g, b, a
            )
        }
    }

    private fun drawCylinder(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        radius: Float,
        height: Float,
        segments: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val half = height / 2f
        val step = (Math.PI * 2.0 / segments)

        for (i in 0 until segments) {

            val a0 = (i * step).toFloat()
            val a1 = ((i + 1) * step).toFloat()

            val c0 = cos(a0)
            val s0 = sin(a0)

            val c1 = cos(a1)
            val s1 = sin(a1)

            // Top ring
            line(
                poseStack,
                consumer,
                radius * c0, half, radius * s0,
                radius * c1, half, radius * s1,
                r, g, b, a
            )

            // Bottom ring
            line(
                poseStack,
                consumer,
                radius * c0, -half, radius * s0,
                radius * c1, -half, radius * s1,
                r, g, b, a
            )

            // Vertical edge
            line(
                poseStack,
                consumer,
                radius * c0, -half, radius * s0,
                radius * c0, half, radius * s0,
                r, g, b, a
            )
        }
    }
}

