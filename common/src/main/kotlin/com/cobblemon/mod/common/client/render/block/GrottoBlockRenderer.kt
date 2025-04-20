/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.BlockState

class GrottoBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<GrottoBlockEntity> {
    override fun render(
        entity: GrottoBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val stateToRender: BlockState = entity.mimickedState
        val level = entity.level ?: return

        poseStack.pushPose()
        Minecraft.getInstance().blockRenderer.renderBatched(
            stateToRender,
            entity.blockPos,
            level,
            poseStack,
            bufferSource.getBuffer(RenderType.solid()),
            true,
            level.random
        )
        poseStack.popPose()

    }
}
