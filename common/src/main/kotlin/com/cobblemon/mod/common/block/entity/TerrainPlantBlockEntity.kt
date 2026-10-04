/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.decor.TerrainPlantBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class TerrainPlantBlockEntity(pos: BlockPos, state: BlockState): BlockEntity(CobblemonBlockEntities.TERRAIN_PLANT, pos, state) {
    companion object {
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, terrainPlantBlockEntity: TerrainPlantBlockEntity) {
            if (level.isClientSide) return
            if (state.block !is TerrainPlantBlock) return

            val glowing = state.getValue(TerrainPlantBlock.GLOWING)

            val wantedState = if (state.`is`(CobblemonBlocks.VOLTACONIA)) level.isThundering
                else if (state.`is`(CobblemonBlocks.VERDROOT)) level.isRaining && !level.isThundering
                else null // We dont control glowing here

            if (wantedState != null && glowing != wantedState) {
                level.setBlock(pos, state.setValue(TerrainPlantBlock.GLOWING, wantedState), Block.UPDATE_CLIENTS)
            }
        }
    }
}