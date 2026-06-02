/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.PhotoPrinterBlock
import com.cobblemon.mod.common.block.entity.PhotoPrinterBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.BlockAndTintGetter

class PhotoPrinterBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<PhotoPrinterBlockEntity> {
    override fun render(
        blockEntity: PhotoPrinterBlockEntity,
        partialTicks: Float,
        matrices: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val printingStack = if (!blockEntity.printingStack.isEmpty) {
            blockEntity.printingStack
        } else {
            blockEntity.getItem(PhotoPrinterBlockEntity.RESULT_SLOT)
        }
        if (printingStack.isEmpty) return

        val progress = if (blockEntity.printingStack.isEmpty) {
            1F
        } else {
            val progressMax = blockEntity.printProgressMax.coerceAtLeast(1)
            ((blockEntity.printProgress + partialTicks) / progressMax).coerceIn(0F, 1F)
        }
        val easedProgress = progress * progress * (3F - (2F * progress))
        val facing = blockEntity.blockState.getValue(PhotoPrinterBlock.FACING)
        val slide = -0.12F + (easedProgress * 0.70F)
        val packedLight = resolvePhotoLight(blockEntity, facing, light)

        matrices.pushPose()
        matrices.translate(
            0.5F + (facing.stepX * slide),
            0.35F, // we can change this to alter the height of where the photo slides out
            0.5F + (facing.stepZ * slide)
        )
        matrices.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)))
        matrices.mulPose(Axis.XP.rotationDegrees(90F))
        matrices.scale(0.85F, 0.85F, 0.85F)

        Minecraft.getInstance().itemRenderer.renderStatic(
            printingStack,
            ItemDisplayContext.FIXED,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            matrices,
            bufferSource,
            blockEntity.level,
            0
        )

        matrices.popPose()
    }

    private fun rotationFor(facing: Direction): Float {
        return when (facing) {
            Direction.NORTH -> 180F
            Direction.EAST -> 270F
            Direction.SOUTH -> 0F
            Direction.WEST -> 90F
            else -> 0F
        }
    }

    private fun resolvePhotoLight(blockEntity: PhotoPrinterBlockEntity, facing: Direction, fallback: Int): Int {
        val level = blockEntity.level as? BlockAndTintGetter ?: return fallback
        val pos = blockEntity.blockPos
        val samples = listOf(
            pos.above(),
            pos.relative(facing),
            pos.relative(facing).above(),
            pos
        ).map { LevelRenderer.getLightColor(level, it) } + fallback

        val sky = samples.maxOf(LightTexture::sky)
        val block = samples.maxOf(LightTexture::block)
        return LightTexture.pack(block, sky)
    }
}
