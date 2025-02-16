package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class StadiumBlockEntity(
    pos: BlockPos,
    blockState: BlockState
): BlockEntity(
    CobblemonBlockEntities.STADIUM,
    pos,
    blockState
) {
    var isInUse = false

    val droneState = DroneState()

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