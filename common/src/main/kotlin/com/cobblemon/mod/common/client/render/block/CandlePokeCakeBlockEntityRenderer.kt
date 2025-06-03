/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.CandlePokeCakeBlock.Companion.CANDLE_COLOR
import com.cobblemon.mod.common.block.CandlePokeCakeBlock.Companion.getCandleByMapColorId
import com.cobblemon.mod.common.block.entity.CakeBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT

class CandlePokeCakeBlockEntityRenderer(
    ctx: BlockEntityRendererProvider.Context,
) : CakeBlockEntityRenderer(ctx) {

    override fun render(
        blockEntity: CakeBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay)

        poseStack.pushPose()
        poseStack.translate(0.0, TOP_LAYER_HEIGHT.toDouble(), 0.0)

        val candleBlock = getCandleByMapColorId(blockEntity.blockState.getValue(CANDLE_COLOR))
        Minecraft.getInstance().blockRenderer.renderSingleBlock(
            candleBlock.defaultBlockState().setValue(LIT, blockEntity.blockState.getValue(LIT)),
            poseStack,
            bufferSource,
            packedLight,
            packedOverlay
        )

        poseStack.popPose()
    }
}