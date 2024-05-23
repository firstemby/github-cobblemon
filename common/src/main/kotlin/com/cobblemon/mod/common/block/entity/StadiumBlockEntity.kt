package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.BlockPos

class StadiumBlockEntity(blockPos: BlockPos?, blockState: BlockState?): BlockEntity(CobblemonBlockEntities.STADIUM, blockPos, blockState) {
    fun getPlayer1Pos(): BlockPos? {
        return pos.add(5, 0, 10)
    }

    fun getPlayer2Pos(): BlockPos? {
        return pos.add(5, 0, -10)
    }

    fun getPoke1Pos(): BlockPos? {
        return pos.add(5, 0, 5)
    }

    fun getPoke2Pos(): BlockPos? {
        return pos.add(5, 0, -5)
    }

}