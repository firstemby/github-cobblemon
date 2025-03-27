package com.cobblemon.mod.common.client.render.block

import net.minecraft.client.color.block.BlockColor
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.GrassColor
import net.minecraft.world.level.block.state.BlockState

object NestLeafBlockColorProvider : BlockColor {
    override fun getColor(
        blockState: BlockState,
        blockAndTintGetter: BlockAndTintGetter?,
        blockPos: BlockPos?,
        i: Int
    ): Int {
        if (blockAndTintGetter == null || blockPos == null) {
            return GrassColor.get(0.5, 1.0)
        }

        return BiomeColors.getAverageFoliageColor(blockAndTintGetter, blockPos)
    }
}