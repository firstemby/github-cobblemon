/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.core.component.DataComponents
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
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

@Suppress("UNUSED_PARAMETER")
class HealingMachineRenderer<T : BlockEntity>(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<T, BlockEntityRenderState> {
    companion object {
        private val offsets = listOf(
            0.2 to 0.385,
            -0.2 to 0.385,
            0.2 to 0.0,
            -0.2 to 0.0,
            0.2 to -0.385,
            -0.2 to -0.385
        )
    }

    private val itemModelResolver: ItemModelResolver = ctx.itemModelResolver()

    override fun createRenderState(): BlockEntityRenderState = HealingMachineRenderState()

    override fun extractRenderState(
        blockEntity: T,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)

        if (blockEntity !is HealingMachineBlockEntity || renderState !is HealingMachineRenderState) {
            return
        }

        renderState.pokeBalls.clear()
        renderState.blockState = blockEntity.blockState
        val level = blockEntity.level ?: return
        blockEntity.pokeBalls().toSortedMap().forEach { (index, pokeBall) ->
            if (index !in offsets.indices) {
                return@forEach
            }

            val renderedStack = pokeBall.stack().copy()
            renderedStack.set(DataComponents.ITEM_MODEL, pokeBall.model3d.withPath { path -> path.removePrefix("item/") })
            val itemState = ItemStackRenderState()
            itemModelResolver.updateForTopItem(itemState, renderedStack, ItemDisplayContext.GROUND, level, null, index)
            if (!itemState.isEmpty) {
                renderState.pokeBalls += HealingMachinePokeBallRenderState(index, itemState)
            }
        }
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is HealingMachineRenderState || renderState.pokeBalls.isEmpty()) {
            return
        }

        val blockState = renderState.blockState ?: return
        val yRot = if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()
        } else {
            180F
        }

        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot))
        poseStack.scale(0.65F, 0.65F, 0.65F)

        renderState.pokeBalls.forEach { pokeBall ->
            poseStack.pushPose()
            val offset = offsets[pokeBall.index]
            poseStack.translate(offset.first, 0.4, offset.second)
            pokeBall.itemState.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
            )
            poseStack.popPose()
        }

        poseStack.popPose()
    }
}

private class HealingMachineRenderState : BlockEntityRenderState() {
    val pokeBalls = mutableListOf<HealingMachinePokeBallRenderState>()
    var blockState: BlockState? = null
}

private data class HealingMachinePokeBallRenderState(
    val index: Int,
    val itemState: ItemStackRenderState
)
