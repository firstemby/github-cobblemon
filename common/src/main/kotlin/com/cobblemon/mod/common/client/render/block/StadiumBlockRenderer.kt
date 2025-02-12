/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.repository.MiscModelRepository
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class StadiumBlockRenderer<T: BlockEntity>(ctx: BlockEntityRendererProvider.Context): BlockEntityRenderer<T> {
    override fun render(blockEntity: T, tickDelta: Float, poseStack: PoseStack, multiBufferSource: MultiBufferSource, light: Int, overlay: Int) {
        if (blockEntity !is StadiumBlockEntity) return
        poseStack.pushPose()

        // Center the drone to aim at block centers instead of the corner
        poseStack.translate(0.5, 0.0, 0.5)
        // Fix upside-down model
        poseStack.mulPose(Axis.XP.rotationDegrees(180F))

        if (blockEntity.isInUse) {
            // TODO
        } else {
            // Rotates the drone to match the block's rotation
            val blockState = if (blockEntity.level != null) blockEntity.blockState
            else (CobblemonBlocks.STADIUM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH) as BlockState)
            val yRot = blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()
            poseStack.mulPose(Axis.YP.rotationDegrees(180F-yRot))

            drawDrone(poseStack, 1.0F, multiBufferSource, light)
        }
    }

    fun drawDrone(
        poseStack: PoseStack,
        scale: Float = 1F,
        multiBufferSource: MultiBufferSource,
        light: Int,
    ) {
        // TODO: move the resource locations to a more sensible location
        val modelResource: ResourceLocation = cobblemonResource("rotom_drone.geo")
        val textureResource: ResourceLocation = cobblemonResource("textures/misc/rotom_drone.png")
        val model = MiscModelRepository.modelOf(modelResource) ?: return

        poseStack.scale(scale, scale, scale)

        val buffer = ItemRenderer.getFoilBufferDirect(multiBufferSource, RenderType.entityCutout(textureResource), false, false)
        // TODO: Make drone lighting dynamic (simply using the light arg makes the drone always appear dark)
        model.render(poseStack, buffer, 200, OverlayTexture.NO_OVERLAY, -0x1)

        poseStack.popPose()
    }
}