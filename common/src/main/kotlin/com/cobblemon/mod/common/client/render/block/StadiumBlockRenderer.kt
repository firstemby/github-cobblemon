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
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneModel
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState

class StadiumBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<StadiumBlockEntity> {
    val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, false)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
    }

    override fun render(blockEntity: StadiumBlockEntity, tickDelta: Float, poseStack: PoseStack, multiBufferSource: MultiBufferSource, light: Int, overlay: Int) {
        val isClientSide = blockEntity.level?.isClientSide == true
        if (!isClientSide) return

        val stadiumBlockState = blockEntity.level?.getBlockState(blockEntity.blockPos) ?: return
        if (stadiumBlockState.block != CobblemonBlocks.STADIUM) {
            // Block has been destroyed/replaced
            return
        }

        val droneState = blockEntity.droneState

        poseStack.pushPose()

        // Center the drone to aim at block centers instead of the corner
        poseStack.translate(0.5, 0.0, 0.5)
        // Fix upside-down model
        poseStack.mulPose(Axis.XP.rotationDegrees(180F))
        poseStack.mulPose(Axis.YP.rotationDegrees(180F))

        if (blockEntity.isInUse) {
            // TODO
        } else {
            // Rotates the drone to match the block's rotation
            val blockState = if (blockEntity.level != null) blockEntity.blockState
            else (CobblemonBlocks.STADIUM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH) as BlockState)
            val yRot = blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()
            poseStack.mulPose(Axis.YP.rotationDegrees(-yRot))

            renderDrone(droneState, "sleep", tickDelta, poseStack, 1.0F, multiBufferSource, light)
        }
    }

    fun renderDrone(
        state: DroneState,
        pose: String,
        tickDelta: Float,
        poseStack: PoseStack,
        scale: Float = 1F,
        multiBufferSource: MultiBufferSource,
        light: Int,
    ) {
        state.updatePartialTicks(tickDelta)

        val droneResource: ResourceLocation = cobblemonResource("rotom_drone")

        val model = VaryingModelRepository.getPoser(droneResource, state) as DroneModel
        val texture = VaryingModelRepository.getTexture(droneResource, state)

        val buffer = multiBufferSource.getBuffer(RenderType.entityCutout(texture))
        model.bufferProvider = multiBufferSource

        state.currentModel = model
        state.setPose(pose)

        model.context = context
        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
        context.put(RenderContext.POSABLE_STATE, state)

        model.applyAnimations(
            entity = null,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = state.animationSeconds * 20
        )

        poseStack.scale(scale, scale, scale)

        // TODO: Make drone lighting dynamic (simply using the light arg makes the drone always appear dark)
        model.withLayerContext(multiBufferSource, state, VaryingModelRepository.getLayers(droneResource, state)) {
            model.render(context, poseStack, buffer, 200, OverlayTexture.NO_OVERLAY, -0x1)
        }
        model.setDefault()
        poseStack.popPose()
    }
}