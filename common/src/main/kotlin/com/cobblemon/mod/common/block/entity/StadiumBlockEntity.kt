package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos

class StadiumBlockEntity(pos: BlockPos, state: BlockState): BlockEntity(CobblemonBlockEntities.STADIUM, pos, state) {
    var isInUse = false

    fun getPlayer1Pos(): BlockPos {
        return blockPos.north(10)
    }
    fun getPlayer2Pos(): BlockPos {
        return blockPos.south(10)
    }
    fun getPoke1Pos(): BlockPos {
        return blockPos.north(5)
    }
    fun getPoke2Pos(): BlockPos {
        return blockPos.south(5)
    }
}