/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.campfirepot.CampfireBlock
import com.cobblemon.mod.common.block.campfirepot.CampfirePotBlock
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.CampfirePotItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelResolver
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CampfireBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<CampfireBlockEntity, BlockEntityRenderState> {
    companion object {
        const val CIRCLE_RADIUS = 0.4F
        const val ROTATION_SPEED = 1.5F
        const val JUMP_AMPLITUDE = 0.025F
        const val JUMP_SPEED = 0.1F
    }

    private val blockModelResolver = ctx.blockModelResolver()
    private val blockDisplayContext = BlockDisplayContext.create()
    private val itemModelResolver: ItemModelResolver = ctx.itemModelResolver()

    override fun createRenderState(): BlockEntityRenderState = CampfireRenderState()

    override fun extractRenderState(
        blockEntity: CampfireBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is CampfireRenderState) {
            return
        }

        renderState.seasonings.clear()
        renderState.facing = blockEntity.blockState.getValue(FACING)
        renderState.rotationFacing = blockEntity.blockState.getValue(CampfireBlock.ITEM_DIRECTION)
        renderState.time = blockEntity.time + tickDelta
        renderState.brothColor = blockEntity.brothColor
        renderState.potState = null

        val campfirePotItem = blockEntity.getPotItem()?.item as? CampfirePotItem ?: return
        val isLidOpen = !blockEntity.blockState.getValue(CampfireBlock.LID)
        val yRot = (renderState.rotationFacing.opposite.toYRot() + renderState.facing.toYRot()) % 360
        renderState.potState = campfirePotItem.block.defaultBlockState()
            .setValue(CampfirePotBlock.OPEN, isLidOpen)
            .setValue(FACING, Direction.fromYRot(yRot.toDouble()))
            .setValue(CampfirePotBlock.OCCUPIED, blockEntity.getSeasonings().isNotEmpty() || blockEntity.getIngredients().isNotEmpty())

        val level = blockEntity.level ?: return
        blockEntity.getSeasonings().forEachIndexed { index, seasoning ->
            val itemState = ItemStackRenderState()
            itemModelResolver.updateForTopItem(itemState, seasoning, ItemDisplayContext.GROUND, level, null, index)
            if (!itemState.isEmpty) {
                renderState.seasonings += itemState
            }
        }
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is CampfireRenderState || renderState.potState == null) {
            return
        }

        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)
        val rotationAngle = when (renderState.facing) {
            Direction.NORTH -> 180F
            Direction.SOUTH -> 0F
            Direction.WEST -> 90F
            Direction.EAST -> -90F
            else -> 0F
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationAngle))
        poseStack.translate(-0.5, -0.5, -0.5)

        val potState = renderState.potState ?: return

        poseStack.pushPose()
        poseStack.translate(0.0, 0.4375, 0.0)
        val blockModelState = BlockModelRenderState()
        blockModelResolver.update(blockModelState, potState, blockDisplayContext)
        blockModelState.tintLayers().add(ARGB.opaque(renderState.brothColor))
        blockModelState.submit(poseStack, submitNodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()

        val gameTime = renderState.time
        val rotation = (gameTime * ROTATION_SPEED) % 360F
        val totalSeasonings = renderState.seasonings.size.coerceAtLeast(1)
        renderState.seasonings.forEachIndexed { index, seasoning ->
            poseStack.pushPose()
            poseStack.scale(0.5F, 0.5F, 0.5F)
            val angleOffset = index * (360F / totalSeasonings.toFloat())
            val angleInRadians = Math.toRadians((rotation + angleOffset).toDouble())
            val xOffset = cos(angleInRadians).toFloat() * CIRCLE_RADIUS
            val zOffset = sin(angleInRadians).toFloat() * CIRCLE_RADIUS
            val jumpOffset = (sin(gameTime * JUMP_SPEED + index * 2F) * JUMP_AMPLITUDE).toDouble()
            poseStack.translate((1F + xOffset).toDouble(), 1.24 + jumpOffset, (1F + zOffset).toDouble())
            val lookYaw = (-Math.toDegrees(atan2(zOffset.toDouble(), xOffset.toDouble())) + 90.0).toFloat()
            poseStack.mulPose(Axis.YP.rotationDegrees(lookYaw))
            poseStack.pushPose()
            poseStack.mulPose(Axis.XP.rotationDegrees(22.5F))
            seasoning.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                index
            )
            poseStack.popPose()
            poseStack.popPose()
        }

        poseStack.popPose()
    }
}

private class CampfireRenderState : BlockEntityRenderState() {
    var facing: Direction = Direction.NORTH
    var rotationFacing: Direction = Direction.NORTH
    var time: Float = 0F
    var brothColor: Int = 0
    var potState: BlockState? = null
    val seasonings = mutableListOf<ItemStackRenderState>()
}
