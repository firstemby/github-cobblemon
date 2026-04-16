/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.LecternBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.phys.Vec3

class LecternBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<LecternBlockEntity, BlockEntityRenderState> {
    private val itemModelResolver: ItemModelResolver = ctx.itemModelResolver()

    override fun createRenderState(): BlockEntityRenderState = LecternRenderState()

    override fun extractRenderState(
        blockEntity: LecternBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is LecternRenderState) {
            return
        }

        renderState.itemState.clear()
        val level = blockEntity.level ?: return
        val stack = blockEntity.getItemStack()
        if (stack.isEmpty) {
            return
        }

        if (blockEntity.blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            renderState.yRot = blockEntity.blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()
        }
        itemModelResolver.updateForTopItem(renderState.itemState, stack, ItemDisplayContext.GROUND, level, null, 0)
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is LecternRenderState || renderState.itemState.isEmpty) {
            return
        }

        poseStack.pushPose()
        poseStack.translate(0.5, 1.17, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(-renderState.yRot))
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5F))
        poseStack.translate(0.0, 0.0, 0.13)
        renderState.itemState.submit(
            poseStack,
            submitNodeCollector,
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            0
        )
        poseStack.popPose()
    }
}

private class LecternRenderState : BlockEntityRenderState() {
    val itemState = ItemStackRenderState()
    var yRot = 0F
}
