/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure
import com.cobblemon.mod.common.block.entity.FossilAnalyzerBlockEntity
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
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class FossilAnalyzerRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<FossilAnalyzerBlockEntity, BlockEntityRenderState> {
    private val itemModelResolver: ItemModelResolver = ctx.itemModelResolver()

    override fun createRenderState(): BlockEntityRenderState = FossilAnalyzerRenderState()

    override fun extractRenderState(
        blockEntity: FossilAnalyzerBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is FossilAnalyzerRenderState) {
            return
        }

        renderState.fossils.clear()
        renderState.blockState = blockEntity.blockState
        val level = blockEntity.level ?: return
        val struct = blockEntity.multiblockStructure as? FossilMultiblockStructure ?: return
        struct.fossilInventory.forEachIndexed { index, fossilStack ->
            if (fossilStack.isEmpty) {
                return@forEachIndexed
            }

            val itemState = ItemStackRenderState()
            itemModelResolver.updateForTopItem(itemState, fossilStack, ItemDisplayContext.NONE, level, null, index)
            if (!itemState.isEmpty) {
                renderState.fossils += FossilRenderData(index, itemState)
            }
        }
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is FossilAnalyzerRenderState || renderState.fossils.isEmpty()) {
            return
        }

        val blockState = renderState.blockState ?: return
        val direction = if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            blockState.getValue(HorizontalDirectionalBlock.FACING)
        } else {
            Direction.SOUTH
        }
        val yRot = direction.toYRot() + if (direction == Direction.WEST || direction == Direction.EAST) 180F else 0F
        val dirOffset = when (direction) {
            Direction.NORTH -> Vec3(0.0, 0.0, 0.05)
            Direction.SOUTH -> Vec3(0.0, 0.0, -0.05)
            Direction.EAST -> Vec3(-0.05, 0.0, 0.0)
            Direction.WEST -> Vec3(0.05, 0.0, 0.0)
            else -> Vec3.ZERO
        }

        renderState.fossils.forEach { fossil ->
            poseStack.pushPose()
            poseStack.translate(0.5 + dirOffset.x, 0.4 + fossil.index * 0.1 + dirOffset.y, 0.5 + dirOffset.z)
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot))
            poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
            poseStack.mulPose(Axis.XP.rotationDegrees(90F))
            poseStack.scale(0.7F, 0.7F, 0.7F)
            fossil.itemState.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                fossil.index
            )
            poseStack.popPose()
        }
    }
}

private class FossilAnalyzerRenderState : BlockEntityRenderState() {
    val fossils = mutableListOf<FossilRenderData>()
    var blockState: BlockState? = null
}

private data class FossilRenderData(
    val index: Int,
    val itemState: ItemStackRenderState
)
