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
import com.cobblemon.mod.common.client.render.SilkLineRenderer
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

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
        SilkLineRenderer.renderStrip(poseStack, buffer, bottom, 1F, light)
        poseStack.popPose()
    }

    private fun findHangingMonCentre(world: Level, pos: BlockPos): Float {
        val mon = world.getEntitiesOfClass(PokemonEntity::class.java, AABB(pos).expandTowards(0.0, -1.0, 0.0))
            .firstOrNull { it.getCurrentPoseType() == PoseType.HANG }
            ?: return 0F
        return (mon.y + mon.bbHeight / 2.0 - pos.y).toFloat()
    }
}