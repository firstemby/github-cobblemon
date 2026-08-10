/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.SilkThreadBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f

/**
 * Renders a silk thread as a camera-facing vertical strip. The bottom-most thread of a column
 * stretches past its block bounds so it visually connects to the centre of the hanging Pokemon.
 */
class SilkThreadRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<SilkThreadBlockEntity> {
    override fun render(
        entity: SilkThreadBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val world = entity.level ?: return
        val pos = entity.blockPos
        val bottom = if (world.getBlockState(pos.below()).`is`(CobblemonBlocks.SILK_THREAD)) 0F else findHangingMonCentre(world, pos)

        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().gameRenderer.mainCamera.yRot))
        drawStrip(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), poseStack.last().pose(), bottom, light)
        poseStack.popPose()
    }

    private fun findHangingMonCentre(world: Level, pos: BlockPos): Float {
        val mon = world.getEntitiesOfClass(PokemonEntity::class.java, AABB(pos).expandTowards(0.0, -1.0, 0.0))
            .firstOrNull { it.getCurrentPoseType() == PoseType.HANG }
            ?: return 0F
        return (mon.y + mon.bbHeight / 2.0 - pos.y).toFloat()
    }

    private fun drawStrip(consumer: VertexConsumer, matrix: Matrix4f, bottom: Float, light: Int) {
        val half = WIDTH / 2
        vertex(consumer, matrix, -half, bottom, U0, 1F, light)
        vertex(consumer, matrix, -half, 1F, U0, 0F, light)
        vertex(consumer, matrix, half, 1F, U1, 0F, light)
        vertex(consumer, matrix, half, bottom, U1, 1F, light)
    }

    private fun vertex(consumer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, u: Float, v: Float, light: Int) {
        consumer.addVertex(matrix, x, y, 0F)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0F, 1F, 0F)
    }

    companion object {
        private val TEXTURE = cobblemonResource("textures/block/silk_thread.png")
        private const val WIDTH = 2F / 16F
        private const val U0 = 6F / 16F
        private const val U1 = 10F / 16F
    }
}
